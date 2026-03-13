ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_bot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS bot_description VARCHAR(255);

INSERT INTO users (
    id,
    phone_number,
    display_name,
    username,
    about,
    phone_privacy,
    last_seen_privacy,
    story_privacy,
    last_seen_at,
    is_bot,
    bot_description,
    created_at
)
VALUES
    (
        '11111111-1111-1111-1111-111111111101',
        'bot-alex-helper',
        'Alex Helper Bot',
        'alex_helper_bot',
        'Built-in assistant for bot commands and demo workflows.',
        'NOBODY',
        'NOBODY',
        'NOBODY',
        NOW(),
        TRUE,
        'Built-in assistant for bot commands and demo workflows.',
        NOW()
    ),
    (
        '11111111-1111-1111-1111-111111111102',
        'bot-alex-echo',
        'Echo Bot',
        'alex_echo_bot',
        'Built-in echo bot for Telegram-style command testing.',
        'NOBODY',
        'NOBODY',
        'NOBODY',
        NOW(),
        TRUE,
        'Built-in echo bot for Telegram-style command testing.',
        NOW()
    )
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    about = EXCLUDED.about,
    phone_privacy = EXCLUDED.phone_privacy,
    last_seen_privacy = EXCLUDED.last_seen_privacy,
    story_privacy = EXCLUDED.story_privacy,
    is_bot = EXCLUDED.is_bot,
    bot_description = EXCLUDED.bot_description;
