FROM python:3.12-slim
WORKDIR /srv
COPY --from=ghcr.io/astral-sh/uv:0.12.2 /uv /usr/local/bin/uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY app app
COPY alembic alembic
COPY alembic.ini .
RUN useradd --create-home --uid 10001 planner && chown -R planner:planner /srv
ENV HOME=/home/planner PYTHONDONTWRITEBYTECODE=1
USER planner
CMD ["uv", "run", "--no-sync", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
