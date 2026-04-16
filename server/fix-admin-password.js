/**
 * Fix admin password — run once from the server directory:
 *   node fix-admin-password.js
 *
 * Generates a fresh bcrypt hash of "admin123" and updates the admins table.
 */

require('dotenv').config();
const bcrypt = require('bcryptjs');
const { Pool } = require('pg');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://localhost/wildwildyeast',
});

(async () => {
  try {
    const hash = await bcrypt.hash('Br3ad$W1ld!2026', 12);
    console.log('Generated hash:', hash);

    const result = await pool.query(
      `INSERT INTO admins (username, password_hash)
       VALUES ('admin', $1)
       ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash
       RETURNING id, username`,
      [hash]
    );

    console.log('Updated admin row:', result.rows[0]);

    // Quick sanity-check
    const row = await pool.query('SELECT password_hash FROM admins WHERE username = $1', ['admin']);
    const valid = await bcrypt.compare('Br3ad$W1ld!2026', row.rows[0].password_hash);
    console.log('Password verification check:', valid ? '✅ PASS' : '❌ FAIL');

    if (!valid) {
      console.error('Hash mismatch — something went wrong.');
      process.exit(1);
    }

    console.log('\nDone. Log in at http://localhost:5174 with admin / Br3ad$W1ld!2026');
  } catch (err) {
    console.error('Error:', err.message);
    process.exit(1);
  } finally {
    await pool.end();
  }
})();
