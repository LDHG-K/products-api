INSERT INTO products (id, name, description, price, stock, category, created_at) VALUES
  (1, 'Wireless Mouse', 'Ergonomic wireless mouse with USB receiver', 19.99, 150, 'Electronics', CURRENT_TIMESTAMP),
  (2, 'Mechanical Keyboard', 'RGB backlit mechanical keyboard, blue switches', 59.99, 80, 'Electronics', CURRENT_TIMESTAMP),
  (3, 'Running Shoes', 'Lightweight running shoes with breathable mesh', 74.50, 45, 'Sportswear', CURRENT_TIMESTAMP),
  (4, 'Yoga Mat', 'Non-slip yoga mat, 6mm thick', 24.90, 200, 'Sportswear', CURRENT_TIMESTAMP),
  (5, 'Coffee Maker', '12-cup programmable coffee maker', 45.00, 30, 'Home Appliances', CURRENT_TIMESTAMP),
  (6, 'Blender', 'High-speed blender with 5 preset programs', 89.99, 20, 'Home Appliances', CURRENT_TIMESTAMP),
  (7, 'Novel: The Silent Sea', 'Bestselling mystery novel, paperback edition', 12.99, 300, 'Books', CURRENT_TIMESTAMP),
  (8, 'Desk Lamp', 'LED desk lamp with adjustable brightness', 29.99, 120, 'Home & Office', CURRENT_TIMESTAMP);

ALTER TABLE products ALTER COLUMN id RESTART WITH 9;
