"""Preserve explicit, versioned capture clarifications independently of original text."""
from alembic import op
import sqlalchemy as sa

revision = "d93b8e401f72"
down_revision = "c81fa728ac09"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("notes", sa.Column("interpretation_hints", sa.JSON(), nullable=False, server_default=sa.text("'{}'")))
    op.create_table("provider_requests", sa.Column("id", sa.String(36), primary_key=True), sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False), sa.Column("kind", sa.String(16), nullable=False), sa.Column("created_at", sa.String(40), nullable=False))
    op.create_index("ix_provider_requests_owner_time", "provider_requests", ["user_id", "created_at"])

def downgrade():
    op.drop_table("provider_requests")
    op.drop_column("notes", "interpretation_hints")
