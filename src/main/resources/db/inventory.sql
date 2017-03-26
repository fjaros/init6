
CREATE TABLE IF NOT EXISTS items (
     id INT(11) NOT NULL AUTO_INCREMENT,
     name VARCHAR(32) NOT NULL,
     special_ability VARCHAR NOT NULL,
     description VARCHAR NOT NULL,
     attack_modifier INT NOT NULL DEFAULT 0,
     defense_modifier INT NOT NULL DEFAULT 0,
     rare NOT NULL BOOLEAN DEFAULT FALSE,
     PRIMARY KEY(id),
     UNIQUE KEY(name)
 );


INSERT INTO items (name, description, attack_modifier, defense_modifier) VALUES
('AlenL\'s LT',
'+10 attack to a channel with more than 200 bots',
'A great man once said \"I don\'t care about your channel wars, but NO MASSBOTS!\"',
10,
0,
true
),
