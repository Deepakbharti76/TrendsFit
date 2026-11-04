-- Optional colour tag for a product image. NULL = shown for every colour (existing images stay as they are).
ALTER TABLE product_images ADD COLUMN color VARCHAR(40) NULL AFTER alt;
