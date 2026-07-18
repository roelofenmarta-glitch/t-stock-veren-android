# T-Stock Magazijnbeheer Veren V10

V10 bevat de V9.2 functies plus een duidelijk voorraad-overzicht, tussentijdse telling, jaar telling, printbare tellijsten/rapporten en C-logo/rapport-branding.

## Installeren

```bash
cd /opt/magazijnbeheer
unzip magazijnbeheer_operatorflow_admin_v10_new_install.zip
cd magazijnbeheer_operatorflow_admin_v10_fresh_install
./scripts/install-new-v10.sh
```

Open daarna:

- Webapp: `http://IP-VAN-DE-VM:8080`
- Adminer: `http://IP-VAN-DE-VM:8081`

Login:

- admin / admin123
- operator / pincode 0000
- voorman / pincode 1111

## Database-volume

V10 gebruikt: `magazijnbeheer_v10_postgres_data`.

Gebruik nooit `docker compose down -v`, `docker volume prune` of `docker system prune --volumes` als je data wilt bewaren.
