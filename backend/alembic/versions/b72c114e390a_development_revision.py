"""Development jobs identify the exact idea revision; older results remain durable."""
from alembic import op

revision = "b72c114e390a"
down_revision = "a15a67f338ba"
branch_labels = None
depends_on = None

def upgrade():
    with op.batch_alter_table("jobs") as batch:
        batch.drop_constraint("uq_job_input", type_="unique")
        batch.create_unique_constraint("uq_job_input", ["note_id", "input_version", "kind", "record_id", "record_version"])

def downgrade():
    # Multiple approved idea revisions cannot be represented by the old uniqueness rule.
    # Refuse the downgrade rather than deleting historical jobs.
    raise RuntimeError("Restore a pre-upgrade backup to downgrade development job identity")
