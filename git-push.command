#!/bin/bash

# Configure SSH to use GitHub's port-443 fallback (bypasses firewalls that block port 22)
mkdir -p ~/.ssh
cat >> ~/.ssh/config << 'EOF'

Host github.com
  Hostname ssh.github.com
  Port 443
  User git
EOF

echo "Testing SSH connection to GitHub..."
ssh -T git@github.com 2>&1

echo ""
echo "Pushing to: $(git remote get-url origin)"
cd ~/Documents/wildwildyeast
git push -u origin main

echo ""
echo "Done! Press any key to close."
read -n 1
