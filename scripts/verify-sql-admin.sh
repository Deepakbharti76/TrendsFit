#!/usr/bin/env bash
# Verifies the V2/V3 migrations (settings, support chat, banners) and the admin-panel SQL against a REAL MySQL/MariaDB server,
# using known test data (revenue sums, refunded/failed exclusion, unread counts...). Creates and drops its own database.
# Usage: DB_CMD="mariadb --socket=/path/s.sock -uroot" bash scripts/verify-sql-admin.sh
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_CMD="${DB_CMD:-mysql -uroot}"
DB="trendsfit_verify_admin"
fail=0
q() { $DB_CMD -N -B "$DB" -e "$1" 2>&1; }
ok() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (expected '$3', got '$2')"; fail=1; fi; }
has() { case "$2" in *"$3"*) echo "PASS  $1";; *) echo "FAIL  $1 (expected to contain '$3', got '$2')"; fail=1;; esac; }
M="$ROOT/backend/src/main/resources/db/migration"

$DB_CMD -e "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" || exit 2
for f in V1__core_schema.sql V2__app_settings.sql V3__admin_panel.sql; do $DB_CMD "$DB" < "$M/$f" || { echo "FAIL  $f"; exit 1; }; done
$DB_CMD "$DB" < "$ROOT/backend/src/main/resources/db/seed-dev/V100__demo_catalog.sql" || { echo "FAIL  V100 seed"; exit 1; }
echo "PASS  V1, V2, V3 and dev seed apply in order"
ok "existing demo data intact"            "$(q "SELECT COUNT(*) FROM products WHERE is_demo=1")" 20

# ---- settings upsert (SettingsService.update)
UP="INSERT INTO app_settings (setting_key, setting_value, updated_by) VALUES ('revenue.target.30d','50000','a@x') ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), updated_by=VALUES(updated_by)"
q "$UP" >/dev/null; q "$UP" >/dev/null
ok "settings upsert keeps one row"        "$(q "SELECT COUNT(*) FROM app_settings")" 1

# ---- revenue test data (known totals)
ins() { q "INSERT INTO orders (order_number,customer_name,email,phone,ship_line1,ship_city,ship_state,ship_pincode,status,payment_method,payment_status,subtotal,total,access_token,created_at) VALUES ('$1','Cust $1','$1@x.test','9876543210','l','Pune','MH','411001','$2','$3','$4',$5,$5,'tok$1',$6)" >/dev/null; }
ins A1 PROCESSING COD COD 1000 "NOW()"                                  # today, counted
ins A2 DELIVERED ONLINE PAID 2000 "NOW() - INTERVAL 2 DAY"              # counted
ins A3 PAYMENT_FAILED ONLINE FAILED 5000 "NOW()"                        # excluded
ins A4 PENDING_PAYMENT ONLINE PENDING 7000 "NOW()"                      # excluded
ins A5 DELIVERED ONLINE REFUNDED 3000 "NOW() - INTERVAL 1 DAY"          # excluded (refunded)
ins A6 PAID ONLINE PAID 4000 "NOW() - INTERVAL 10 DAY"                  # previous period for a 7-day window
RW="status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND payment_status <> 'REFUNDED'"
ok "revenue last 7 days = 3000 (failed/pending/refunded excluded)" "$(q "SELECT COALESCE(SUM(total),0) FROM orders WHERE $RW AND created_at >= CURDATE() - INTERVAL 6 DAY AND created_at < CURDATE() + INTERVAL 1 DAY")" "3000.00"
ok "revenue today = 1000"                 "$(q "SELECT COALESCE(SUM(total),0) FROM orders WHERE $RW AND created_at >= CURDATE() AND created_at < CURDATE() + INTERVAL 1 DAY")" "1000.00"
ok "previous 7-day period = 4000"         "$(q "SELECT COALESCE(SUM(total),0) FROM orders WHERE $RW AND created_at >= CURDATE() - INTERVAL 13 DAY AND created_at < CURDATE() - INTERVAL 6 DAY")" "4000.00"
ok "daily groups: 2 days with revenue"    "$(q "SELECT COUNT(*) FROM (SELECT DATE(created_at) d, SUM(total) r, COUNT(*) n FROM orders WHERE $RW AND created_at >= CURDATE() - INTERVAL 6 DAY AND created_at < CURDATE() + INTERVAL 1 DAY GROUP BY DATE(created_at)) t")" 2
ok "each order counted once (count=2)"    "$(q "SELECT COUNT(*) FROM orders WHERE $RW AND created_at >= CURDATE() - INTERVAL 6 DAY")" 2
ok "by payment method rows"               "$(q "SELECT COUNT(*) FROM (SELECT payment_method, COUNT(*) o, COALESCE(SUM(total),0) r FROM orders WHERE $RW AND created_at >= CURDATE() - INTERVAL 6 DAY GROUP BY payment_method) t")" 2
q "SELECT status, COUNT(*) AS orders FROM orders WHERE created_at >= CURDATE() - INTERVAL 29 DAY GROUP BY status ORDER BY orders DESC" >/dev/null && echo "PASS  breakdown: by status"

# ---- KPI queries
ok "KPI to fulfil (PAID/PROCESSING/PACKED) = 2" "$(q "SELECT COUNT(*) FROM orders WHERE status IN ('PAID','PROCESSING','PACKED')")" 2
ok "KPI payment failed 30d = 1"           "$(q "SELECT COUNT(*) FROM orders WHERE status='PAYMENT_FAILED' AND created_at >= CURDATE() - INTERVAL 29 DAY")" 1
ok "KPI pending COD count/amount"         "$(q "SELECT CONCAT(COUNT(*),'/',COALESCE(SUM(total),0)) FROM orders WHERE payment_method='COD' AND status IN ('PROCESSING','PACKED','SHIPPED','OUT_FOR_DELIVERY')")" "1/1000.00"
q "UPDATE product_variants SET stock = 0 WHERE id = (SELECT id FROM (SELECT MIN(id) id FROM product_variants) x)" >/dev/null
q "UPDATE product_variants SET stock = 2 WHERE id = (SELECT id FROM (SELECT MIN(id)+1 id FROM product_variants) x)" >/dev/null
ok "KPI out-of-stock >= 1"                "$([ "$(q "SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock = 0")" -ge 1 ] && echo yes)" yes
ok "KPI low-stock includes stock 2 (threshold 5)" "$([ "$(q "SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.active=1 AND p.status='ACTIVE' AND v.stock > 0 AND v.stock <= 5")" -ge 1 ] && echo yes)" yes

# ---- orders list filters (payment method/status)
ok "orders filter by payment method COD"  "$(q "SELECT COUNT(*) FROM orders o WHERE 1=1 AND o.payment_method = 'COD'")" 1
ok "orders filter by payment status PAID" "$(q "SELECT COUNT(*) FROM orders o WHERE 1=1 AND o.payment_status = 'PAID'")" 2

# ---- payments / shipments / COD lists
O=$(q "SELECT id FROM orders WHERE order_number='A2'")
q "INSERT INTO payments (order_id, provider_order_id, provider_payment_id, amount, status) VALUES ($O,'order_1','pay_1',2000,'PAID')" >/dev/null
q "INSERT INTO payments (order_id, provider_order_id, amount, status, failure_reason) VALUES ($(q "SELECT id FROM orders WHERE order_number='A3'"),'order_2',5000,'FAILED','card declined')" >/dev/null
ok "payments list joins orders"           "$(q "SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE (LOWER(o.order_number) LIKE '%a2%' OR LOWER(p.provider_payment_id) LIKE '%a2%')")" 1
ok "payments summary groups by status"    "$(q "SELECT COUNT(*) FROM (SELECT status, COUNT(*) c, COALESCE(SUM(amount),0) a FROM payments GROUP BY status) t")" 2
ok "COD collected (delivered) = 0"        "$(q "SELECT COUNT(*) FROM orders WHERE payment_method='COD' AND status='DELIVERED'")" 0
P=$(q "SELECT id FROM orders WHERE order_number='A1'")
ok "ready-to-ship lists A1 (processing, no shipment)" "$(q "SELECT o.order_number FROM orders o WHERE o.status='PROCESSING' AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.order_id=o.id AND s.kind='FORWARD')")" A1
q "INSERT INTO shipments (order_id, awb, courier, status) VALUES ($P,'AWB9','Courier X','IN_TRANSIT')" >/dev/null
ok "ready-to-ship empty after a shipment" "$(q "SELECT COUNT(*) FROM orders o WHERE o.status='PROCESSING' AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.order_id=o.id AND s.kind='FORWARD')")" 0
ok "shipments list joins orders"          "$(q "SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id=s.order_id WHERE LOWER(s.awb) LIKE '%awb9%'")" 1

# ---- categories (list with counts, delete guard)
ok "category list with product counts"    "$(q "SELECT COUNT(*) FROM (SELECT c.id, (SELECT COUNT(*) FROM products p WHERE p.category_id = c.id) AS productCount FROM categories c) t")" 3
has "duplicate category slug rejected"    "$(q "INSERT INTO categories (slug,name) VALUES ('jeans','Dup')")" "Duplicate"
q "INSERT INTO categories (slug,name,sort_order) VALUES ('socks','Socks',9)" >/dev/null
ok "empty category has 0 products (deletable)" "$(q "SELECT COUNT(*) FROM products WHERE category_id=(SELECT id FROM categories WHERE slug='socks')")" 0
ok "category with products is in use"     "$([ "$(q "SELECT COUNT(*) FROM products WHERE category_id=(SELECT id FROM categories WHERE slug='jeans')")" -gt 0 ] && echo yes)" yes

# ---- banners
q "INSERT INTO banners (title, image_url, link_url, active, sort_order) VALUES ('Sale','/uploads/banners/a.jpg','/shop',1,1),('Hidden',NULL,NULL,0,0)" >/dev/null
ok "only active banners are public"       "$(q "SELECT COUNT(*) FROM banners WHERE active = 1")" 1

# ---- support chat
q "INSERT INTO support_conversations (access_token, customer_name, customer_email, order_number) VALUES ('t1','Asha','asha@x.test','A2')" >/dev/null
C=$(q "SELECT id FROM support_conversations LIMIT 1")
q "INSERT INTO support_messages (conversation_id, sender, body) VALUES ($C,'CUSTOMER','Where is my order?'),($C,'CUSTOMER','Hello?')" >/dev/null
UNREAD="SELECT COUNT(*) FROM support_messages WHERE sender='CUSTOMER' AND read_at IS NULL"
ok "unread total = 2"                     "$(q "$UNREAD")" 2
LIST="SELECT c.id, (SELECT COUNT(*) FROM support_messages m WHERE m.conversation_id = c.id AND m.sender = 'CUSTOMER' AND m.read_at IS NULL) AS unread, (SELECT m2.body FROM support_messages m2 WHERE m2.conversation_id = c.id ORDER BY m2.id DESC LIMIT 1) AS lastMessage FROM support_conversations c WHERE 1=1 AND (LOWER(c.customer_name) LIKE '%asha%' OR LOWER(c.customer_email) LIKE '%asha%' OR LOWER(c.order_number) LIKE '%asha%') AND EXISTS (SELECT 1 FROM support_messages u WHERE u.conversation_id = c.id AND u.sender = 'CUSTOMER' AND u.read_at IS NULL) ORDER BY c.last_message_at DESC, c.id DESC LIMIT 30 OFFSET 0"
ok "conversation list: unread count + last message" "$(q "$LIST" | tr '\t' '|')" "$C|2|Hello?"
q "UPDATE support_messages SET read_at = NOW() WHERE conversation_id = $C AND sender = 'CUSTOMER' AND read_at IS NULL" >/dev/null
ok "opening a chat marks messages read"   "$(q "$UNREAD")" 0
q "INSERT INTO support_messages (conversation_id, sender, staff_email, body) VALUES ($C,'STAFF','s@x.test','On its way')" >/dev/null
ok "staff message unread by customer until fetched" "$(q "SELECT COUNT(*) FROM support_messages WHERE conversation_id=$C AND sender='STAFF' AND read_at IS NULL")" 1
q "UPDATE support_messages SET read_at = NOW() WHERE conversation_id = $C AND sender = 'STAFF' AND read_at IS NULL" >/dev/null
ok "customer fetch marks staff message Seen" "$(q "SELECT COUNT(*) FROM support_messages WHERE conversation_id=$C AND sender='STAFF' AND read_at IS NOT NULL")" 1
has "invalid sender rejected"             "$(q "INSERT INTO support_messages (conversation_id, sender, body) VALUES ($C,'BOT','x')")" "ERROR"
has "invalid chat status rejected"        "$(q "UPDATE support_conversations SET status='WEIRD' WHERE id=$C")" "ERROR"
ok "5-chats-per-day guard query works"    "$(q "SELECT COUNT(*) FROM support_conversations WHERE customer_email='asha@x.test' AND created_at >= (NOW() - INTERVAL 1 DAY)")" 1
ok "order/email match check"              "$(q "SELECT COUNT(*) FROM orders WHERE order_number='A2' AND LOWER(email)='a2@x.test'")" 1
q "DELETE FROM support_conversations WHERE id=$C" >/dev/null
ok "deleting a chat cascades its messages" "$(q "SELECT COUNT(*) FROM support_messages")" 0

# ---- notifications
q "SELECT order_number, total, payment_method, created_at FROM orders WHERE created_at >= NOW() - INTERVAL 1 DAY AND status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') ORDER BY id DESC LIMIT 10" >/dev/null && echo "PASS  notifications: new orders"
q "SELECT order_number, total, created_at FROM orders WHERE status='PAYMENT_FAILED' AND created_at >= NOW() - INTERVAL 7 DAY ORDER BY id DESC LIMIT 10" >/dev/null && echo "PASS  notifications: payment failures"
q "SELECT e.id, o.order_number, e.created_at FROM exchanges e JOIN orders o ON o.id=e.order_id WHERE e.status IN ('PENDING','INFO_REQUESTED') ORDER BY e.id DESC LIMIT 5" >/dev/null && echo "PASS  notifications: exchanges"
q "SELECT o.order_number, p.status, p.amount, p.updated_at FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.status IN ('REFUND_SENT','REFUNDED') AND p.updated_at >= NOW() - INTERVAL 7 DAY ORDER BY p.id DESC LIMIT 5" >/dev/null && echo "PASS  notifications: refunds"
q "SELECT c.id, c.customer_name, MAX(m.created_at) AS at, COUNT(*) AS n FROM support_conversations c JOIN support_messages m ON m.conversation_id=c.id WHERE m.sender='CUSTOMER' AND m.read_at IS NULL GROUP BY c.id, c.customer_name ORDER BY at DESC LIMIT 10" >/dev/null && echo "PASS  notifications: unread messages (ONLY_FULL_GROUP_BY safe)"

$DB_CMD -e "DROP DATABASE $DB"
[ $fail -eq 0 ] && echo "ALL ADMIN SQL CHECKS PASSED" || echo "SOME ADMIN SQL CHECKS FAILED"
exit $fail
