const express = require('express');
const { body, validationResult } = require('express-validator');
const db = require('../db');
const { authenticateAdmin } = require('../middleware/auth');

const router = express.Router();

// GET /api/products — public
router.get('/', async (req, res) => {
  try {
    const result = await db.query('SELECT * FROM products ORDER BY category, name');
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// POST /api/products — admin
router.post('/', authenticateAdmin, [
  body('name').trim().notEmpty(),
  body('category').trim().notEmpty(),
  body('price_paise').isInt({ min: 1 }),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ error: errors.array()[0].msg });

  const { name, category, description, price_paise, available } = req.body;
  try {
    const result = await db.query(
      `INSERT INTO products (name, category, description, price_paise, available)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [name, category, description || null, price_paise, available !== false]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// PUT /api/products/:id — admin
router.put('/:id', authenticateAdmin, async (req, res) => {
  const { name, category, description, price_paise, available } = req.body;
  try {
    const result = await db.query(
      `UPDATE products SET
        name = COALESCE($1, name),
        category = COALESCE($2, category),
        description = COALESCE($3, description),
        price_paise = COALESCE($4, price_paise),
        available = COALESCE($5, available)
       WHERE id = $6 RETURNING *`,
      [name, category, description, price_paise, available, req.params.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Product not found' });
    res.json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

// DELETE /api/products/:id — admin
router.delete('/:id', authenticateAdmin, async (req, res) => {
  try {
    await db.query('DELETE FROM products WHERE id = $1', [req.params.id]);
    res.status(204).end();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
