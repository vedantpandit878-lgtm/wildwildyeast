const express = require('express');
const { body, validationResult } = require('express-validator');
const db = require('../db');
const { authenticateAdmin } = require('../middleware/auth');

const router = express.Router();

// GET /api/orders — admin, supports ?status= ?flat= ?date=
router.get('/', authenticateAdmin, async (req, res) => {
  const { status, flat, date } = req.query;
  let conditions = [];
  let params = [];
  let i = 1;

  if (status) { conditions.push(`o.status = $${i++}`); params.push(status); }
  if (flat) { conditions.push(`o.flat_number ILIKE $${i++}`); params.push(`%${flat}%`); }
  if (date) { conditions.push(`o.created_at::date = $${i++}`); params.push(date); }

  const where = conditions.length ? 'WHERE ' + conditions.join(' AND ') : '';

  try {
    const result = await db.query(`
      SELECT o.*,
        json_agg(json_build_object(
          'id', oi.id,
          'product_id', oi.product_id,
          'product_name', oi.product_name,
          'quantity', oi.quantity,
          'unit_price_paise', oi.unit_price_paise
        )) AS items
      FROM orders o
      LEFT JOIN order_items oi ON oi.order_id = o.id
      ${where}
      GROUP BY o.id
      ORDER BY o.created_at DESC
    `, params);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/orders/:id
router.get('/:id', async (req, res) => {
  try {
    const result = await db.query(`
      SELECT o.*,
        json_agg(json_build_object(
          'id', oi.id,
          'product_id', oi.product_id,
          'product_name', oi.product_name,
          'quantity', oi.quantity,
          'unit_price_paise', oi.unit_price_paise
        )) AS items
      FROM orders o
      LEFT JOIN order_items oi ON oi.order_id = o.id
      WHERE o.id = $1
      GROUP BY o.id
    `, [req.params.id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Order not found' });
    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/orders — public, creates order + items in transaction, upserts customer
router.post('/', [
  body('flat_number').trim().notEmpty(),
  body('customer_name').trim().notEmpty(),
  body('items').isArray({ min: 1 }),
  body('items.*.product_id').notEmpty(),
  body('items.*.quantity').isInt({ min: 1 }),
  body('items.*.unit_price_paise').isInt({ min: 0 }),
  body('items.*.product_name').notEmpty(),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ error: errors.array()[0].msg });

  const { flat_number, customer_name, items, payment_method, notes } = req.body;
  const total_paise = items.reduce((sum, item) => sum + item.unit_price_paise * item.quantity, 0);
  const client = await db.pool.connect();

  try {
    await client.query('BEGIN');

    // Upsert customer
    const custResult = await client.query(`
      INSERT INTO customers (name, flat_number)
      VALUES ($1, $2)
      ON CONFLICT (flat_number) DO UPDATE SET name = EXCLUDED.name
      RETURNING *
    `, [customer_name, flat_number.toUpperCase()]);
    const customer = custResult.rows[0];

    // Create order
    const orderResult = await client.query(`
      INSERT INTO orders (customer_id, flat_number, customer_name, payment_method, total_paise, notes)
      VALUES ($1, $2, $3, $4, $5, $6) RETURNING *
    `, [customer.id, flat_number.toUpperCase(), customer_name, payment_method || 'cash', total_paise, notes || null]);
    const order = orderResult.rows[0];

    // Create order items
    for (const item of items) {
      await client.query(`
        INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price_paise)
        VALUES ($1, $2, $3, $4, $5)
      `, [order.id, item.product_id, item.product_name, item.quantity, item.unit_price_paise]);
    }

    await client.query('COMMIT');

    // Fetch full order with items
    const fullOrder = await db.query(`
      SELECT o.*,
        json_agg(json_build_object(
          'id', oi.id,
          'product_id', oi.product_id,
          'product_name', oi.product_name,
          'quantity', oi.quantity,
          'unit_price_paise', oi.unit_price_paise
        )) AS items
      FROM orders o
      LEFT JOIN order_items oi ON oi.order_id = o.id
      WHERE o.id = $1
      GROUP BY o.id
    `, [order.id]);

    res.status(201).json(fullOrder.rows[0]);
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  } finally {
    client.release();
  }
});

// PATCH /api/orders/:id/status — admin
router.patch('/:id/status', authenticateAdmin, [
  body('status').isIn(['pending', 'confirmed', 'delivered']),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ error: 'Invalid status' });

  try {
    const result = await db.query(
      `UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2 RETURNING *`,
      [req.body.status, req.params.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Order not found' });
    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/customers/:flat_number/orders
router.get('/by-flat/:flat_number', async (req, res) => {
  try {
    const result = await db.query(`
      SELECT o.*,
        json_agg(json_build_object(
          'id', oi.id,
          'product_id', oi.product_id,
          'product_name', oi.product_name,
          'quantity', oi.quantity,
          'unit_price_paise', oi.unit_price_paise
        )) AS items
      FROM orders o
      LEFT JOIN order_items oi ON oi.order_id = o.id
      WHERE o.flat_number = $1
      GROUP BY o.id
      ORDER BY o.created_at DESC
    `, [req.params.flat_number.toUpperCase()]);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
