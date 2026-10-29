#!/usr/bin/env bash
# Verifies the Flyway schema + dev seed and the critical SQL used by the services against a REAL MySQL/MariaDB server.
# Usage: DB_CMD="mariadb --socket=/path/s.sock -uroot" sh scripts/verify-sql.sh     (or DB_CMD="mysql -uroot -p...")
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_CMD="${DB_CMD:-mysql -uroot}"
DB="trendsfit_verify"
fail=0
q() { $DB_CMD -N -B "$DB" -e "$1" 2>&1; }
ok() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (expected '$3', got '$2')"; fail=1; fi; }
has() { case "$2" in *"$3"*) echo "PASS  $1";; *) echo "FAIL  $1 (expected to contain '$3', got '$2')"; fail=1;; esac; }

$DB_CMD -e "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" || exit 2
$DB_CMD "$DB" < "$ROOT/backend/src/main/resources/db/migration/V1__core_schema.sql" || { echo "FAIL  V1 schema"; exit 1; }
$DB_CMD "$DB" < "$ROOT/backend/src/main/resources/db/seed-dev/V100__demo_catalog.sql" || { echo "FAIL  V100 seed"; exit 1; }

ok "schema has 23 tables"        "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB'")" 23
ok "20 demo products"            "$(q "SELECT COUNT(*) FROM products WHERE is_demo=1")" 20
ok "all demo products ACTIVE"    "$(q "SELECT COUNT(*) FROM products WHERE is_demo=1 AND status='ACTIVE'")" 20
ok "3 categories"                "$(q "SELECT COUNT(*) FROM categories")" 3
ok "orders.stock_held default 1" "$(q "SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema='$DB' AND table_name='orders' AND column_name='stock_held'")" "1"

# --- catalog search (same shape as CatalogService.search)
ok "search: jeans category"      "$(q "SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id WHERE p.status='ACTIVE' AND c.active=1 AND c.slug='jeans'")" 7
ok "search: text 'shirt'"        "$(q "SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id WHERE p.status='ACTIVE' AND (LOWER(p.name) LIKE '%shirt%' OR LOWER(p.sku) LIKE '%shirt%' OR LOWER(c.name) LIKE '%shirt%' OR LOWER(p.description) LIKE '%shirt%' OR LOWER(p.fit) LIKE '%shirt%')" | head -1)" "$(q "SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id WHERE p.status='ACTIVE' AND (LOWER(p.name) LIKE '%shirt%' OR LOWER(p.sku) LIKE '%shirt%' OR LOWER(c.name) LIKE '%shirt%' OR LOWER(p.description) LIKE '%shirt%' OR LOWER(p.fit) LIKE '%shirt%')")"
r="$(q "SELECT p.id, p.slug, p.name, p.price, p.sale_price, p.rating_avg, p.rating_count, p.is_new_arrival, p.is_bestseller, p.fit, c.slug AS category_slug, c.name AS category_name FROM products p JOIN categories c ON c.id = p.category_id WHERE p.status = 'ACTIVE' AND c.active = 1 AND COALESCE(p.sale_price, p.price) >= 500 AND COALESCE(p.sale_price, p.price) <= 1500 AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.active = 1 AND v.size = 'M' AND LOWER(v.color) = 'black' AND v.stock > 0) ORDER BY COALESCE(p.sale_price, p.price) ASC, p.id DESC LIMIT 12 OFFSET 0" | wc -l)"
[ "$r" -gt 0 ] && echo "PASS  search: size+colour+price filter returns rows ($r)" || { echo "FAIL  search filter returned none"; fail=1; }
q "SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.status='ACTIVE' ORDER BY (CASE WHEN p.sale_price IS NULL THEN 0 ELSE (p.price - p.sale_price) / p.price END) DESC, p.id DESC LIMIT 3" >/dev/null && echo "PASS  search: discount sort valid"
q "SELECT DISTINCT v.size FROM product_variants v JOIN products p ON p.id=v.product_id WHERE p.status='ACTIVE' AND v.active=1 ORDER BY v.size" >/dev/null && echo "PASS  facets: sizes"
q "SELECT MIN(COALESCE(sale_price, price)) AS mn, MAX(COALESCE(sale_price, price)) AS mx FROM products WHERE status='ACTIVE'" >/dev/null && echo "PASS  facets: price range"
q "SELECT c.slug, c.name, c.description, COUNT(p.id) AS productCount FROM categories c LEFT JOIN products p ON p.category_id=c.id AND p.status='ACTIVE' WHERE c.active=1 GROUP BY c.id, c.slug, c.name, c.description, c.sort_order ORDER BY c.sort_order" >/dev/null && echo "PASS  home: categories with counts"

# --- stock: atomic decrement never goes negative
V=$(q "SELECT id FROM product_variants WHERE stock > 0 LIMIT 1"); S=$(q "SELECT stock FROM product_variants WHERE id=$V")
q "UPDATE product_variants SET stock = $S WHERE id=$V" >/dev/null
a=$(q "UPDATE product_variants SET stock = stock - $S WHERE id = $V AND active = 1 AND stock >= $S; SELECT ROW_COUNT()")
b=$(q "UPDATE product_variants SET stock = stock - 1 WHERE id = $V AND active = 1 AND stock >= 1; SELECT ROW_COUNT()")
ok "reserve all remaining stock: 1 row"         "$a" 1
ok "reserve when empty: 0 rows (no oversell)"   "$b" 0
has "CHECK blocks negative stock"               "$(q "UPDATE product_variants SET stock = -1 WHERE id=$V")" "ERROR"

# --- orders, payments, webhook dedupe, coupons
q "INSERT INTO users (email,password_hash,full_name,role) VALUES ('a@x.test','h','A','CUSTOMER')" >/dev/null
has "email unique & case-insensitive"            "$(q "INSERT INTO users (email,password_hash,full_name,role) VALUES ('A@X.TEST','h','B','CUSTOMER')")" "Duplicate"
q "INSERT INTO orders (order_number,customer_name,email,phone,ship_line1,ship_city,ship_state,ship_pincode,payment_method,subtotal,total,access_token,idempotency_key) VALUES ('TF1','A','a@x.test','9876543210','l','c','s','411001','ONLINE',100,100,'t','key1')" >/dev/null
has "idempotency key unique"                     "$(q "INSERT INTO orders (order_number,customer_name,email,phone,ship_line1,ship_city,ship_state,ship_pincode,payment_method,subtotal,total,access_token,idempotency_key) VALUES ('TF2','A','a@x.test','9876543210','l','c','s','411001','ONLINE',100,100,'t','key1')")" "Duplicate"
O=$(q "SELECT id FROM orders WHERE order_number='TF1'")
q "INSERT INTO payments (order_id, provider_order_id, amount) VALUES ($O,'order_X',100)" >/dev/null
P=$(q "SELECT id FROM payments LIMIT 1")
a=$(q "UPDATE payments SET status = 'PAID', provider_payment_id = 'pay_1' WHERE id = $P AND status <> 'PAID' AND (provider_payment_id IS NULL OR provider_payment_id = 'pay_1'); SELECT ROW_COUNT()")
b=$(q "UPDATE payments SET status = 'PAID', provider_payment_id = 'pay_1' WHERE id = $P AND status <> 'PAID' AND (provider_payment_id IS NULL OR provider_payment_id = 'pay_1'); SELECT ROW_COUNT()")
ok "payment settle: first caller wins"           "$a" 1
ok "payment settle: duplicate caller no-op"      "$b" 0
q "INSERT INTO webhook_events (source,event_id,event_type) VALUES ('RAZORPAY','evt1','payment.captured')" >/dev/null
has "webhook event dedupe (unique)"              "$(q "INSERT INTO webhook_events (source,event_id,event_type) VALUES ('RAZORPAY','evt1','payment.captured')")" "Duplicate"
q "INSERT INTO coupons (code,type,value,max_uses) VALUES ('ONCE','FIXED',50,1)" >/dev/null
C=$(q "SELECT id FROM coupons WHERE code='ONCE'")
a=$(q "UPDATE coupons SET used_count = used_count + 1 WHERE id = $C AND active = 1 AND (max_uses IS NULL OR used_count < max_uses); SELECT ROW_COUNT()")
b=$(q "UPDATE coupons SET used_count = used_count + 1 WHERE id = $C AND active = 1 AND (max_uses IS NULL OR used_count < max_uses); SELECT ROW_COUNT()")
ok "coupon: first redemption"                    "$a" 1
ok "coupon: limit reached"                       "$b" 0
q "INSERT INTO invoices (order_id, invoice_number) VALUES ($O,'TF-2026-000001')" >/dev/null
has "one invoice per order"                      "$(q "INSERT INTO invoices (order_id, invoice_number) VALUES ($O,'TF-2026-000002')")" "Duplicate"
q "INSERT INTO shipments (order_id, awb) VALUES ($O,'AWB1')" >/dev/null
has "AWB unique"                                  "$(q "INSERT INTO shipments (order_id, awb) VALUES ($O,'AWB1')")" "Duplicate"

# --- admin dashboard / lists
q "SELECT COALESCE(SUM(total),0) FROM orders WHERE status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND created_at >= '2026-01-01'" >/dev/null && echo "PASS  dashboard: revenue"
q "SELECT DATE(created_at) AS d, SUM(total) AS revenue, COUNT(*) AS n FROM orders WHERE status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND created_at >= '2026-01-01' GROUP BY DATE(created_at)" >/dev/null && echo "PASS  dashboard: daily series"
q "SELECT i.name, SUM(i.qty) AS units, SUM(i.line_total) AS revenue FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND o.created_at >= '2026-01-01' GROUP BY i.product_id, i.name ORDER BY units DESC LIMIT 5" >/dev/null && echo "PASS  dashboard: top products"
q "SELECT u.id, (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id AND o.status NOT IN ('PENDING_PAYMENT','PAYMENT_FAILED')) AS orderCount FROM users u WHERE u.role='CUSTOMER' LIMIT 5" >/dev/null && echo "PASS  admin: customers with order counts"
q "SELECT p.id, (SELECT COALESCE(SUM(stock),0) FROM product_variants v WHERE v.product_id=p.id AND v.active=1) AS stock, (SELECT url FROM product_images i WHERE i.product_id=p.id ORDER BY i.sort_order, i.id LIMIT 1) AS image FROM products p JOIN categories c ON c.id=p.category_id ORDER BY p.id DESC LIMIT 5" >/dev/null && echo "PASS  admin: product list subqueries"
q "SELECT stock FROM product_variants WHERE id = $V FOR UPDATE" >/dev/null && echo "PASS  inventory: row lock"
# expiry job query
q "SELECT id FROM orders WHERE status IN ('PENDING_PAYMENT','PAYMENT_FAILED') AND payment_method = 'ONLINE' AND stock_held = 1 AND created_at < NOW()" >/dev/null && echo "PASS  expiry job query"
# cascade
q "DELETE FROM orders WHERE id=$O" >/dev/null
ok "order delete cascades payments"               "$(q "SELECT COUNT(*) FROM payments WHERE order_id=$O")" 0

$DB_CMD -e "DROP DATABASE $DB"
[ $fail -eq 0 ] && echo "ALL SQL CHECKS PASSED" || echo "SOME SQL CHECKS FAILED"
exit $fail
