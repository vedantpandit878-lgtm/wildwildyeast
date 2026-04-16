# Wild Wild Yeast Ferments — Order Management System

Good bread, honestly made.

A mobile-first bread ordering system for a small artisan bakery. Customers order from their phones using their flat number. The admin dashboard tracks everything.

---

## Prerequisites

- Node.js 18+
- PostgreSQL 14+

---

## Setup

### 1. Database

Create the database and run the migration:

```bash
createdb wildwildyeast
psql wildwildyeast < server/src/db/migrations/001_initial.sql
```

This creates all tables and seeds 9 bread products + the default admin user.

### 2. Server environment

```bash
cp server/.env.example server/.env
```

Edit `server/.env`:

```
DATABASE_URL=postgres://your_user:your_password@localhost:5432/wildwildyeast
JWT_SECRET=pick_a_long_random_string_here
PORT=3001
```

### 3. Logo images

Drop both logo files into the public folders of both frontends:

```
client-customer/public/logo-light.png   ← black text on white circle (light backgrounds)
client-customer/public/logo-dark.png    ← white text on black circle (dark backgrounds)
client-admin/public/logo-light.png
client-admin/public/logo-dark.png
```

---

## Running

Install dependencies and start each service in a separate terminal:

```bash
# Terminal 1 — API server (port 3001)
cd server
npm install
npm run dev

# Terminal 2 — Customer app (port 5173)
cd client-customer
npm install
npm run dev

# Terminal 3 — Admin dashboard (port 5174)
cd client-admin
npm install
npm run dev
```

Or run all three concurrently from the root (install `concurrently` first):

```bash
npm install -g concurrently
concurrently \
  "cd server && npm run dev" \
  "cd client-customer && npm run dev" \
  "cd client-admin && npm run dev"
```

---

## URLs

| Service          | URL                       |
|------------------|---------------------------|
| API              | http://localhost:3001/api |
| Customer app     | http://localhost:5173     |
| Admin dashboard  | http://localhost:5174     |

---

## Default admin credentials

```
Username: admin
Password: admin123
```

**Change the password hash in production.** Generate a new one:

```js
const bcrypt = require('bcryptjs')
bcrypt.hash('your_new_password', 10).then(console.log)
```

Then update the row in the `admins` table:

```sql
UPDATE admins SET password_hash = '<new_hash>' WHERE username = 'admin';
```

---

## Environment variables

| Variable       | Description                                  |
|----------------|----------------------------------------------|
| `DATABASE_URL` | PostgreSQL connection string                 |
| `JWT_SECRET`   | Secret for signing admin JWTs (keep it long) |
| `PORT`         | Server port (default: 3001)                  |

---

## Project structure

```
/
├── server/
│   ├── src/
│   │   ├── routes/         products, orders, customers, auth
│   │   ├── middleware/      auth.js (JWT verification)
│   │   ├── db/              index.js + migrations/
│   │   └── index.js
│   ├── .env.example
│   └── package.json
│
├── client-customer/        Mobile-first customer ordering app
│   ├── src/
│   │   ├── pages/          Login, Shop, OrderConfirm, MyOrders, Profile
│   │   ├── components/     ProductCard, CategoryPills, CartSheet, BottomNav, StatusTracker
│   │   ├── api/            products.js, orders.js, customers.js
│   │   └── App.jsx
│   └── package.json
│
├── client-admin/           Admin dashboard (tablet/desktop)
│   ├── src/
│   │   ├── pages/          Login, Dashboard, Customers, Products
│   │   ├── components/     OrderTable, OrderDrawer, MetricCard, ProductGrid
│   │   ├── api/            index.js
│   │   └── App.jsx
│   └── package.json
│
└── README.md
```

---

## Notes

- All prices are stored as **paise** (integer) in the database. Divide by 100 to display in ₹.
- Customer auth is flat-number only — no passwords, no OTP. Friction is the enemy.
- Admin JWT tokens expire after 7 days.
- The `VITE_API_URL` environment variable overrides the default `http://localhost:3001/api` in both frontends — useful for deploying to a staging server.
