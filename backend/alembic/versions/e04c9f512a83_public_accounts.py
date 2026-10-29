"""Public verified-email accounts, one-time recovery, Google identities and bounded login flows."""
from alembic import op
import sqlalchemy as sa
revision = "e04c9f512a83"
down_revision = "d93b8e401f72"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("users", sa.Column("email", sa.String(320), nullable=True))
    op.add_column("users", sa.Column("password_hash", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("email_verified", sa.Boolean(), nullable=False, server_default=sa.false()))
    op.create_index("ix_users_email", "users", ["email"], unique=True)
    op.create_table("account_tokens", sa.Column("digest", sa.String(64), primary_key=True), sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False), sa.Column("purpose", sa.String(16), nullable=False), sa.Column("expires_at", sa.String(40), nullable=False), sa.Column("used", sa.Boolean(), nullable=False))
    op.create_index("ix_account_tokens_user_id", "account_tokens", ["user_id"])
    op.create_table("external_identities", sa.Column("issuer", sa.String(200), primary_key=True), sa.Column("subject", sa.String(255), primary_key=True), sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False))
    op.create_index("ix_external_identities_user_id", "external_identities", ["user_id"])
    op.create_table("login_flows", sa.Column("state_digest", sa.String(64), primary_key=True), sa.Column("nonce", sa.String(100), nullable=False), sa.Column("challenge", sa.String(100), nullable=False), sa.Column("expires_at", sa.String(40), nullable=False), sa.Column("link_user_id", sa.String(36), nullable=True), sa.Column("user_id", sa.String(36), nullable=True), sa.Column("handoff_digest", sa.String(64), nullable=True), sa.Column("used", sa.Boolean(), nullable=False), sa.Column("callback_used", sa.Boolean(), nullable=False))
    op.create_table("auth_rates", sa.Column("bucket", sa.String(64), primary_key=True), sa.Column("attempts", sa.Integer(), nullable=False), sa.Column("expires_at", sa.String(40), nullable=False))
    op.create_index("ix_auth_rates_expires_at", "auth_rates", ["expires_at"])
    op.create_table("service_heartbeats", sa.Column("name", sa.String(100), primary_key=True), sa.Column("seen_at", sa.String(40), nullable=False))
    op.create_index("ix_service_heartbeats_seen_at", "service_heartbeats", ["seen_at"])

def downgrade():
    for table in ("service_heartbeats", "auth_rates", "login_flows", "external_identities", "account_tokens"): op.drop_table(table)
    op.drop_index("ix_users_email", table_name="users")
    for column in ("email_verified", "password_hash", "email"): op.drop_column("users", column)
