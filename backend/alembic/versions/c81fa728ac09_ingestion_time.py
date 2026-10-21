"""Server ingestion time controls rate limiting independently of client capture dates."""
from alembic import op
import sqlalchemy as sa

revision = "c81fa728ac09"
down_revision = "b72c114e390a"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("notes", sa.Column("received_at", sa.String(40), nullable=True))
    op.execute("UPDATE notes SET received_at = created_at")
    with op.batch_alter_table("notes") as batch:
        batch.alter_column("received_at", existing_type=sa.String(40), nullable=False)
        batch.create_index("ix_notes_received_at", ["received_at"])

def downgrade():
    with op.batch_alter_table("notes") as batch:
        batch.drop_index("ix_notes_received_at")
        batch.drop_column("received_at")
