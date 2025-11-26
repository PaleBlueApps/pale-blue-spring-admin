PRAGMA foreign_keys = ON;

-- USERS
CREATE TABLE IF NOT EXISTS users (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     username TEXT NOT NULL,
     email TEXT NOT NULL UNIQUE,
     age INTEGER NOT NULL
);

-- ROLES
CREATE TABLE IF NOT EXISTS roles (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     name TEXT NOT NULL UNIQUE
);

-- POSTS (FK → users)
CREATE TABLE IF NOT EXISTS posts (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     title TEXT NOT NULL,
     content TEXT,
     user_id INTEGER NOT NULL,
     FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);

-- USER_ROLES (FK → users, roles)
CREATE TABLE IF NOT EXISTS user_roles (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     user_id INTEGER NOT NULL,
     role_id INTEGER NOT NULL,
     FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
     FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE,
     UNIQUE(user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_userroles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_userroles_role ON user_roles(role_id);

--------------------------------------------------
-- INSERT DEMO DATA
--------------------------------------------------

INSERT INTO users (id, username, email, age) VALUES
             (1, 'john_doe', 'john@example.com', 28),
             (2, 'alice_smith', 'alice@example.com', 32),
             (3, 'biplab', 'biplab.d@example.com', 26),
             (4, 'michael', 'michael.m@example.com', 30),
             (5, 'mike', 'mike.y@example.com', 24),
             (6, 'anil', 'anil.b@example.com', 35),
             (7, 'emma_watson', 'emma.watson@example.com', 27),
             (8, 'oliver_brown', 'oliver.brown@example.com', 31),
             (9, 'noah_white', 'noah.white@example.com', 29),
             (10, 'ava_scott', 'ava.scott@example.com', 22),
             (11, 'harry_liu', 'harry.liu@example.com', 33),
             (12, 'mia_garcia', 'mia.garcia@example.com', 25);

INSERT INTO roles (id, name) VALUES
            (1, 'ADMIN'),
            (2, 'EDITOR'),
            (3, 'VIEWER');

INSERT INTO posts (id, title, content, user_id) VALUES
        (1, 'First Post', 'Welcome to the example application!', 1),
        (2, 'Admin Tools', 'This shows how the Admin UI works.', 1),
        (3, 'Deep Dive into Spring', 'A technical article about Spring Boot.', 2),
        (4, 'Hello World', 'Testing post creation.', 2),
        (5, 'SQLite Tips', 'Some tips for using SQLite effectively.', 3),
        (6, 'Kotlin & JPA', 'Kotlin works well with JPA in most cases.', 3),
        (7, 'System Design Basics', 'Explaining system design for beginners.', 4),
        (8, 'Caching Strategies', 'Using Redis and in-memory caching.', 4),
        (9, 'UI Design Principles', 'Minimalistic UI design is the future.', 5),
        (10, 'Compose for Desktop', 'JetBrains Compose is growing fast.', 5),
        (11, 'Effective Testing', 'Unit tests & integration tests.', 6),
        (12, 'Logging Best Practices', 'How to log effectively.', 6),
        (13, 'Docker 101', 'Introduction to Docker for devs.', 7),
        (14, 'Kubernetes Crash Course', 'K8s is powerful but complex.', 7),
        (15, 'Secrets Management', 'Keep secrets safe using Vault.', 8),
        (16, 'CI/CD Guide', 'Automating deployments the easy way.', 8),
        (17, 'Database Indexing', 'Improve performance with indexes.', 9),
        (18, 'Query Optimization', 'Optimizing SQL queries.', 9),
        (19, 'Intro to Flutter', 'Flutter is extremely productive.', 10),
        (20, 'Flutter State Mgmt', 'Riverpod, Bloc & Provider compared.', 10),
        (21, 'AI Trends', 'Latest updates in AI and ML.', 11),
        (22, 'Prompt Engineering', 'Build better prompts for LLMs.', 11),
        (23, 'DevOps Mindset', 'DevOps is a culture, not a role.', 12),
        (24, 'Monitoring 101', 'Using Prometheus & Grafana.', 12),
        (25, 'Security Audits', 'How to perform internal audits.', 6),
        (26, 'SOLID Principles', 'A good dev must know SOLID.', 8),
        (27, 'Lifecycle Management', 'Managing entity lifecycle in JPA.', 4),
        (28, 'Advanced Compose', 'Animations & layouts.', 5),
        (29, 'Multi-module Apps', 'Scaling Android apps.', 10),
        (30, 'Distributed Systems', 'CAP theorem explained.', 9);

INSERT INTO user_roles (user_id, role_id) VALUES
      (1, 1), (1, 3),
      (2, 2),
      (3, 3),
      (4, 2), (4, 3),
      (5, 3),
      (6, 1),
      (7, 2),
      (8, 3),
      (9, 3),
      (10, 2),
      (11, 1), (11, 3),
      (12, 3);
