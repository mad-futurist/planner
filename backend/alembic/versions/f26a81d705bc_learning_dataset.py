"""Append-only learning examples, contribution consent and synthetic provenance."""
from alembic import op
import sqlalchemy as sa

revision = "f26a81d705bc"
down_revision = "e04c9f512a83"
branch_labels = depends_on = None


def upgrade():
    op.add_column("users", sa.Column("dataset_consent", sa.Boolean(), nullable=False, server_default=sa.false()))
    op.add_column("users", sa.Column("dataset_consent_at", sa.String(40)))
    op.add_column("users", sa.Column("dataset_synthetic", sa.Boolean(), nullable=False, server_default=sa.false()))
    op.create_table("learning_samples",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id")),
        sa.Column("event_key", sa.String(200), nullable=False, unique=True),
        sa.Column("group_key", sa.String(160), nullable=False),
        sa.Column("origin", sa.String(24), nullable=False),
        sa.Column("event_kind", sa.String(32), nullable=False),
        sa.Column("label_source", sa.String(32), nullable=False),
        sa.Column("schema_version", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.String(40), nullable=False),
        sa.Column("data", sa.JSON(), nullable=False))
    op.create_index("ix_learning_owner_created", "learning_samples", ["user_id", "created_at", "id"])
    op.create_index("ix_learning_group", "learning_samples", ["group_key"])


def downgrade():
    raise RuntimeError("Learning history is preserved; restore a pre-upgrade backup instead of dropping it")
