CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS products (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT,
  price_paise INTEGER NOT NULL,
  available BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customers (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  flat_number TEXT NOT NULL UNIQUE,
  phone TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orders (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID REFERENCES customers(id),
  flat_number TEXT NOT NULL,
  customer_name TEXT NOT NULL,
  payment_method TEXT DEFAULT 'cash',
  status TEXT DEFAULT 'pending' CHECK (status IN ('pending','confirmed','delivered')),
  total_paise INTEGER NOT NULL,
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_items (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id),
  product_name TEXT NOT NULL,
  quantity INTEGER NOT NULL,
  unit_price_paise INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS admins (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL
);

-- Seed products
INSERT INTO products (name, category, description, price_paise, available) VALUES
('Sourdough Loaf', 'Loaves', 'Wild-fermented, long-proofed. The one everyone orders.', 28000, true),
('Multigrain Bread', 'Loaves', 'Seeds, grains, and good intentions.', 22000, true),
('Rye Bread', 'Loaves', 'Dense, earthy, 80% dark rye. Not for the faint of palette.', 25000, true),
('Whole Wheat Baguette', 'Baguettes', 'Crisp crust, light crumb. Excellent with anything.', 16000, true),
('Sesame Baguette', 'Baguettes', 'Classic baguette. Toasted sesame on top.', 17000, true),
('Focaccia', 'Flatbreads', 'Olive oil, rosemary, sea salt. Simple. Perfect.', 20000, true),
('Pita Bread', 'Flatbreads', 'Soft, pocketable, pairs with everything in your fridge.', 12000, true),
('Brioche', 'Enriched', 'Butter-rich, pillowy. You deserve it.', 30000, true),
('Cinnamon Roll Loaf', 'Enriched', 'Swirled cinnamon sugar. Slice warm. No regrets.', 32000, true)
ON CONFLICT DO NOTHING;

-- Seed admin (password: Br3ad$W1ld!2026)
INSERT INTO admins (username, password_hash) VALUES
('admin', '$2a$12$KQJ8KiHCaWZaiGLRKXn/2u4lSp6Nxa.rwAGJsDqLicaAkR4X5x9uO')
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;
