#!/bin/bash
# Detect local Wi-Fi IP
IP=$(ipconfig getifaddr en0)
if [ -z "$IP" ]; then
  echo "ERROR: Could not detect IP on en0. Check your Wi-Fi connection."
  exit 1
fi

# Write .env.local with the real IP
echo "VITE_API_URL=http://$IP:3000/api" > ~/Documents/wildwildyeast/client-customer/.env.local
echo "✓ .env.local updated: VITE_API_URL=http://$IP:3000/api"

# Start the customer frontend
cd ~/Documents/wildwildyeast/client-customer
npm run dev -- --host
