-- Profile photo (DP). NULL = no photo; the storefront shows the default profile icon.
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(300) NULL;
