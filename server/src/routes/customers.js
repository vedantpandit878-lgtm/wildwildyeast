const express = require('express');
const { body, validationResult } = require('express-validator');
const db = require('../db');
const { authenticateAdmin } = require('../middleware/auth');

const router = express.Router();

// GET /api/customers — admin
router.get('/', authenticateAdmin, async (req, res) => {
  try {
    const result = await db.query(`
      SELECT c.*,
        COUNT(o.id)::int AS total_orders,
        MAX(o.created_at) AS last_order_at
      FROM customers c
      LEFT JOIN orders o ON o.customer_id = c.id
      GROUP BY c.id
      ORDER BY c.flat_number
    `);
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/customers — upsert by flat_number (public)
router.post('/', [
  body('flat_number').trim().notEmpty(),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ error: 'Flat number required' });

  const { flat_number, name, phone } = req.body;
  try {
    // Check if exists
    const existing = await db.query('SELECT * FROM customers WHERE flat_number = $1', [flat_number.toUpperCase()]);
    if (existing.rows.length > 0) return res.json(existing.rows[0]);

    // If name provided, create
    if (name) {
      const result = await db.query(
        'INSERT INTO customers (name, flat_number, phone) VALUES ($1, $2, $3) RETURNING *',
        [name, flat_number.toUpperCase(), phone || null]
      );
      return res.status(201).json(result.rows[0]);
    }

    // Flat not found, name not provided — signal new customer
    res.status(404).json({ new_customer: true, flat_number: flat_number.toUpperCase() });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// GET /api/customers/:flat_number
router.get('/:flat_number', async (req, res) => {
  try {
    const result = await db.query(
      'SELECT * FROM customers WHERE flat_number = $1',
      [req.params.flat_number.toUpperCase()]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Customer not found' });
    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
