ALTER TABLE users
    ADD COLUMN IF NOT EXISTS bot_supports_inline BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS bot_web_app_url VARCHAR(512);

UPDATE users
SET bot_supports_inline = TRUE
WHERE username IN ('alex_helper_bot', 'alex_echo_bot');

UPDATE users
SET bot_web_app_url = 'https://example.com/alex-helper-mini-app'
WHERE username = 'alex_helper_bot';
