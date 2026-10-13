# Schema export (helper for writing Flyway migrations)

`.github/workflows/schema.yml` starts the app once on an empty PostgreSQL 16 database with Hibernate
creating the tables, then saves:

- `hibernate-create.sql`: the DDL Hibernate generates for the current entities
- `pg_dump.sql`: what PostgreSQL actually ended up with (`pg_dump --schema-only`)

to the `schema-export` branch. Use them as the starting point for a new migration in
`src/main/resources/db/migration/`, then delete anything Flyway already created. It is a helper only:
the app itself never creates tables (Flyway does, and Hibernate only validates).

Run it from GitHub: Actions → "Schema export" → Run workflow, or push a change to this folder.
