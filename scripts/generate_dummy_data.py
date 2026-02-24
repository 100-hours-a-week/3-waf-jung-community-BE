#!/usr/bin/env python3
"""
Dummy Data Generator
Generates half the scale of the original 4.8MB data

Scale:
- Users: 600
- Posts: 1,500
- Comments: ~18,000 (10~15 per post)
- Likes: 600 (focused on post_id=1500)
"""

import random
from datetime import datetime, timedelta
import hashlib

# Configuration
NUM_USERS = 600
NUM_POSTS = 1500
COMMENTS_PER_POST = (10, 15)  # min, max
NUM_LIKES = 600  # focused on post_id=1500
LIKE_TARGET_POST = 1500

# BCrypt hash (password: "Test1234!")
BCRYPT_HASH = "$2a$10$o.WKDo1.tSSOux8fMnPoeuWOtLMjFGTlieOd9p4HwkBcQWHyBkkX6"

# English name data
FIRST_NAMES = ["James", "John", "Robert", "Michael", "David", "William", "Richard", "Joseph", "Thomas", "Chris",
               "Emma", "Olivia", "Ava", "Sophia", "Isabella", "Mia", "Charlotte", "Amelia", "Harper", "Evelyn",
               "Daniel", "Matthew", "Anthony", "Mark", "Steven", "Paul", "Andrew", "Joshua", "Kevin", "Brian",
               "Emily", "Abigail", "Ella", "Avery", "Sofia", "Grace", "Lily", "Chloe", "Victoria", "Natalie"]
LAST_NAMES = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Wilson", "Moore",
              "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin", "Lee", "Clark", "Lewis"]

# Post title templates (max 27 chars)
POST_TITLES = [
    "Great day today", "New beginnings", "Restaurant recs please",
    "Weekend plans?", "Need some advice", "Good morning everyone",
    "Nice weather today", "Ready to go home", "Lunch suggestions",
    "Started working out", "Book recommendations", "Just got back from trip",
    "Looking for hobbies", "Coffee anyone?", "Random thoughts",
    "Quick question!", "Sharing this", "My review", "Thank you all",
    "How do I do this?", "Finally made it!", "Any recommendations?",
]

# Post content templates
POST_CONTENTS = [
    "Hello everyone! Hope you have a great day. Keep going!",
    "I would love to hear your thoughts. Let me know in the comments!",
    "It was a really good experience. You should all try it.",
    "This happened to me. What do you think about it?",
    "Wanted to share this with everyone. Hope you find it useful!",
    "First time posting here. Nice to meet you all!",
    "Just documenting what happened today.",
    "Has anyone else experienced this?",
    "Got this recommendation and it was amazing!",
    "I have a question about this.",
]

# Comment templates (max 200 chars)
COMMENTS = [
    "Great post!", "I totally agree!", "I think so too",
    "Thanks for sharing!", "Very helpful info", "Please post more",
    "Good read", "Cheering for you!", "You got this!", "Liked and saved",
    "Oh this is nice", "I should try this", "Thanks for the info",
    "Completely agree", "Awesome!", "Amazing", "So jealous!",
    "I was wondering too", "This helped a lot", "Thanks for this",
    "Have a great day!", "Looking forward to more",
]


def random_date(start_date, end_date):
    """Generate random date"""
    delta = end_date - start_date
    random_days = random.randint(0, delta.days)
    random_seconds = random.randint(0, 86400)
    return start_date + timedelta(days=random_days, seconds=random_seconds)


def generate_nickname(index):
    """Generate unique nickname (max 10 chars)"""
    first = random.choice(FIRST_NAMES)
    last = random.choice(LAST_NAMES)
    # Use first name initial + last name pattern for shorter nicknames
    name = first[0] + last

    # Add number for uniqueness if needed
    if index > len(LAST_NAMES) * len(FIRST_NAMES) * 0.5:
        name = name[:7] + str(index % 100)

    return name[:10]  # 10 char limit


def escape_sql(text):
    """Escape SQL string"""
    return text.replace("'", "''").replace("\\", "\\\\")


def main():
    output_file = "03_insert_dummy_small.sql"

    # Date range
    start_date = datetime(2024, 10, 1)
    end_date = datetime(2025, 10, 23)

    # Prevent nickname duplicates
    used_nicknames = set()

    with open(output_file, "w", encoding="utf-8") as f:
        # Header
        f.write(f"""-- =============================================
-- Dummy Data Generation Script (Small)
-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
-- =============================================
-- Users: {NUM_USERS}
-- Posts: {NUM_POSTS}
-- Comments: {COMMENTS_PER_POST[0]}~{COMMENTS_PER_POST[1]} per post
-- Likes: {NUM_LIKES} (post_id={LIKE_TARGET_POST})
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;

""")

        # 1. Users
        f.write(f"-- =============================================\n")
        f.write(f"-- 1. Users ({NUM_USERS} rows)\n")
        f.write(f"-- =============================================\n")
        f.write("INSERT INTO users (email, password_hash, nickname, role, user_status, created_at, updated_at, image_id) VALUES\n")

        user_rows = []
        for i in range(1, NUM_USERS + 1):
            email = f"user{i}@test.com"

            # Generate unique nickname
            nickname = generate_nickname(i)
            while nickname in used_nicknames:
                nickname = generate_nickname(i) + str(random.randint(1, 99))
                nickname = nickname[:10]
            used_nicknames.add(nickname)

            created = random_date(start_date, end_date)
            created_str = created.strftime('%Y-%m-%d %H:%M:%S')

            user_rows.append(
                f"('{email}', '{BCRYPT_HASH}', '{nickname}', 'USER', 'ACTIVE', '{created_str}', '{created_str}', NULL)"
            )

        f.write(",\n".join(user_rows))
        f.write(";\n\n")

        # 2. Posts
        f.write(f"-- =============================================\n")
        f.write(f"-- 2. Posts ({NUM_POSTS} rows)\n")
        f.write(f"-- =============================================\n")
        f.write("INSERT INTO posts (post_title, post_content, created_at, updated_at, post_status, user_id) VALUES\n")

        post_rows = []
        post_dates = []  # Store dates for comment generation
        for i in range(1, NUM_POSTS + 1):
            user_id = random.randint(1, NUM_USERS)
            title = escape_sql(random.choice(POST_TITLES))
            content = escape_sql(random.choice(POST_CONTENTS))

            # Add number to title for uniqueness
            if random.random() > 0.5:
                title = f"{title} #{i}"
            title = title[:27]  # 27 char limit

            created = random_date(start_date, end_date)
            created_str = created.strftime('%Y-%m-%d %H:%M:%S')
            post_dates.append(created)

            post_rows.append(
                f"('{title}', '{content}', '{created_str}', '{created_str}', 'ACTIVE', {user_id})"
            )

        f.write(",\n".join(post_rows))
        f.write(";\n\n")

        # 3. Post Stats
        f.write(f"-- =============================================\n")
        f.write(f"-- 3. Post Stats ({NUM_POSTS} rows)\n")
        f.write(f"-- =============================================\n")
        f.write("INSERT INTO post_stats (post_id, like_count, comment_count, view_count) VALUES\n")

        stats_rows = []
        comment_counts = []  # Store actual comment counts
        for i in range(1, NUM_POSTS + 1):
            comment_count = random.randint(COMMENTS_PER_POST[0], COMMENTS_PER_POST[1])
            comment_counts.append(comment_count)

            like_count = NUM_LIKES if i == LIKE_TARGET_POST else 0
            view_count = random.randint(comment_count, comment_count * 10)

            stats_rows.append(f"({i}, {like_count}, {comment_count}, {view_count})")

        f.write(",\n".join(stats_rows))
        f.write(";\n\n")

        # 4. Comments
        total_comments = sum(comment_counts)
        f.write(f"-- =============================================\n")
        f.write(f"-- 4. Comments ({total_comments} rows)\n")
        f.write(f"-- =============================================\n")

        # Batch insert (considering MySQL max_allowed_packet)
        BATCH_SIZE = 1000
        comment_batch = []
        comment_id = 0

        for post_id in range(1, NUM_POSTS + 1):
            post_date = post_dates[post_id - 1]
            num_comments = comment_counts[post_id - 1]

            for _ in range(num_comments):
                comment_id += 1
                user_id = random.randint(1, NUM_USERS)
                content = escape_sql(random.choice(COMMENTS))

                # Comments are written after the post
                comment_date = post_date + timedelta(
                    hours=random.randint(1, 720),
                    minutes=random.randint(0, 59)
                )
                if comment_date > end_date:
                    comment_date = end_date
                created_str = comment_date.strftime('%Y-%m-%d %H:%M:%S')

                comment_batch.append(
                    f"('{content}', '{created_str}', '{created_str}', 'ACTIVE', {post_id}, {user_id})"
                )

                # Write batch
                if len(comment_batch) >= BATCH_SIZE:
                    f.write("INSERT INTO comments (comment_content, created_at, updated_at, comment_status, post_id, user_id) VALUES\n")
                    f.write(",\n".join(comment_batch))
                    f.write(";\n\n")
                    comment_batch = []

        # Write remaining comments
        if comment_batch:
            f.write("INSERT INTO comments (comment_content, created_at, updated_at, comment_status, post_id, user_id) VALUES\n")
            f.write(",\n".join(comment_batch))
            f.write(";\n\n")

        # 5. Post Likes
        f.write(f"-- =============================================\n")
        f.write(f"-- 5. Post Likes ({NUM_LIKES} rows) - post_id={LIKE_TARGET_POST}\n")
        f.write(f"-- =============================================\n")
        f.write("INSERT INTO post_likes (user_id, post_id, created_at) VALUES\n")

        like_rows = []
        like_users = random.sample(range(1, NUM_USERS + 1), min(NUM_LIKES, NUM_USERS))
        target_post_date = post_dates[LIKE_TARGET_POST - 1]

        for user_id in like_users:
            like_date = target_post_date + timedelta(
                hours=random.randint(1, 168),
                minutes=random.randint(0, 59)
            )
            if like_date > end_date:
                like_date = end_date
            created_str = like_date.strftime('%Y-%m-%d %H:%M:%S')

            like_rows.append(f"({user_id}, {LIKE_TARGET_POST}, '{created_str}')")

        f.write(",\n".join(like_rows))
        f.write(";\n\n")

        # Footer
        f.write("""SET FOREIGN_KEY_CHECKS = 1;

-- Completion message
SELECT 'Dummy data (small) inserted successfully!' AS status;
SELECT
    (SELECT COUNT(*) FROM users) AS users,
    (SELECT COUNT(*) FROM posts) AS posts,
    (SELECT COUNT(*) FROM comments) AS comments,
    (SELECT COUNT(*) FROM post_likes) AS likes;
""")

    print(f"✅ {output_file} generated successfully!")
    print(f"   - Users: {NUM_USERS}")
    print(f"   - Posts: {NUM_POSTS}")
    print(f"   - Comments: ~{total_comments}")
    print(f"   - Likes: {NUM_LIKES} (post_id={LIKE_TARGET_POST})")


if __name__ == "__main__":
    main()
