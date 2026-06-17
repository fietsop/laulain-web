#!/bin/bash
# ============================================================
#  Laulain Luxe Rentals — Nginx Setup Script
#
#  Sets up Nginx as a reverse proxy for:
#    - luxrentals.laulaine.com → Spring Boot app (port 8081)
#    - jenkins.laulaine.com    → Jenkins (port 8080)
#
#  Usage:
#    chmod +x scripts/setup-nginx.sh
#    sudo ./scripts/setup-nginx.sh
#
#  Run once on a fresh Amazon Linux 2 / Amazon Linux 2023 server.
# ============================================================

set -e  # Exit on any error

APP_DOMAIN="luxrentals.laulaine.com"
JENKINS_DOMAIN="jenkins.laulaine.com"
APP_PORT=8081
JENKINS_PORT=8080

echo "============================================"
echo " Laulain Luxe Rentals — Nginx Setup"
echo "============================================"

# ---- Step 1: Install Nginx ----
echo ""
echo "[1/4] Installing Nginx..."

if command -v nginx &> /dev/null; then
    echo "  Nginx already installed — skipping install."
else
    # Try Amazon Linux 2023 first, fall back to Amazon Linux 2
    if dnf list nginx &> /dev/null 2>&1; then
        sudo dnf install nginx -y
    elif amazon-linux-extras list | grep nginx &> /dev/null 2>&1; then
        sudo amazon-linux-extras install nginx1 -y
    else
        sudo yum install nginx -y
    fi
    echo "  Nginx installed."
fi

# ---- Step 2: Create virtual host configs ----
echo ""
echo "[2/4] Creating Nginx virtual host configs..."

# App config
sudo tee /etc/nginx/conf.d/laulain-app.conf > /dev/null <<EOF
server {
    listen 80;
    server_name ${APP_DOMAIN};

    # Increase timeouts for slow Spring Boot startup
    proxy_connect_timeout 60s;
    proxy_send_timeout    60s;
    proxy_read_timeout    60s;

    location / {
        proxy_pass http://localhost:${APP_PORT};
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # Health check endpoint — no logging
    location /actuator/health {
        proxy_pass http://localhost:${APP_PORT}/actuator/health;
        access_log off;
    }
}
EOF
echo "  Created: /etc/nginx/conf.d/laulain-app.conf"

# Jenkins config
sudo tee /etc/nginx/conf.d/jenkins.conf > /dev/null <<EOF
server {
    listen 80;
    server_name ${JENKINS_DOMAIN};

    location / {
        proxy_pass         http://localhost:${JENKINS_PORT};
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_set_header   X-Forwarded-Host  \$host;
        proxy_redirect     http://localhost:${JENKINS_PORT} http://${JENKINS_DOMAIN};

        # Required for Jenkins websocket agents
        proxy_http_version 1.1;
        proxy_set_header   Upgrade    \$http_upgrade;
        proxy_set_header   Connection "upgrade";
    }
}
EOF
echo "  Created: /etc/nginx/conf.d/jenkins.conf"

# ---- Step 3: Test and reload Nginx ----
echo ""
echo "[3/4] Testing Nginx configuration..."
sudo nginx -t

echo ""
echo "[4/4] Starting / reloading Nginx..."
sudo systemctl enable nginx
sudo systemctl restart nginx

# ---- Done ----
echo ""
echo "============================================"
echo " Setup complete!"
echo "============================================"
echo ""
echo " App URL:     http://${APP_DOMAIN}"
echo " Jenkins URL: http://${JENKINS_DOMAIN}"
echo ""
echo " Next steps:"
echo "   1. Add DNS A records pointing both domains to this server's IP:"
echo "      $(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo '<your-ec2-ip>')"
echo ""
echo "   2. Open ports 80 and 443 in your EC2 Security Group."
echo ""
echo "   3. (Optional) Add HTTPS with Certbot:"
echo "      sudo yum install certbot python3-certbot-nginx -y"
echo "      sudo certbot --nginx -d ${APP_DOMAIN} -d ${JENKINS_DOMAIN}"
echo ""


##just test