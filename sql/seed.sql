-- DRS-Enhanced demonstration data.
-- Run once, after schema.sql, on an empty drs database:
--     mysql -u root -p < sql/schema.sql
--     mysql -u root -p drs < sql/seed.sql
-- The four accounts are the demonstration accounts from the README; their
-- password is Password1, stored only as a salted hash. Codes follow the
-- row ids, as the server would assign them.

USE drs;

INSERT INTO users (user_id, username, full_name, role, department,
                   password_salt, password_hash, active)
VALUES
    ('USR-001', 'coordinator', 'Alex Coordinator', 'COORDINATOR', NULL,
     'MXEU60jg8CIi0tgirOMpyQ==',
     'BIbF0OPPaw6eH4DZgjnixgsbs3Obyn5dmw3IGTEpF40=', TRUE),
    ('USR-002', 'liaison', 'Sam Liaison', 'LIAISON', 'FIRE_AND_EMERGENCY',
     'MCkIFq9jKmkyYBW+ScPbdQ==',
     'WgXWGL3RIDmk2eDI3WkuDSf0r6Kb/PG6HN6yGPv3DCQ=', TRUE),
    ('USR-003', 'responder', 'Jo Responder', 'RESPONDER', NULL,
     'GzmMxVQz2RlqI0snxUe4Jg==',
     'CVn9vF4Q84DGcBhJmuHsf4quyl7TreZUCxNocJxaD+w=', TRUE),
    ('USR-004', 'admin', 'Robin Administrator', 'ADMINISTRATOR', NULL,
     'leXboNeXrVnpxnS1U5WJUg==',
     'zxKg1ok6Q4HYbUcW0MjFVQKqcsRtOQwQBrcXECE6m5I=', TRUE);

INSERT INTO reports (report_id, disaster_type, address_line, suburb,
                     latitude, longitude, observed_at, submitted_at,
                     description, immediate_risks, reporter_name,
                     reporter_contact, status, linked_incident_id)
VALUES
    ('RPT-0001', 'FIRE', '2 Dock Road', 'Laverton', -37.8636, 144.7700,
     '2026-10-12 09:10:00', '2026-10-12 09:14:00',
     'Smoke and flames from a warehouse roof',
     'Gas cylinders stored on site', 'Priya Shah', '0400 111 222',
     'VALIDATED', 'INC-0001'),
    ('RPT-0002', 'FLOOD', '15 River Street', 'Maribyrnong', -37.7740,
     144.8890, '2026-10-12 10:02:00', '2026-10-12 10:05:00',
     'Water over the road near the bridge', 'Cars stranded',
     'Tom Nguyen', 'tom.nguyen@example.com', 'SUBMITTED', NULL);

INSERT INTO incidents (incident_id, title, disaster_type, address_line,
                       suburb, latitude, longitude, status, priority,
                       created_at, first_response_at, version)
VALUES
    ('INC-0001', 'Fire - Laverton', 'FIRE', '2 Dock Road', 'Laverton',
     -37.8636, 144.7700, 'ACTIVE', 'P1', '2026-10-12 09:20:00',
     '2026-10-12 09:31:00', 2);

INSERT INTO incident_reports (incident_id, report_id)
VALUES ('INC-0001', 'RPT-0001');

INSERT INTO assessments (assessment_id, incident_id, threat_to_life, scale,
                         infrastructure_impact, source_confidence,
                         calculated_score, recommended_priority,
                         confirmed_priority, assessed_by, assessed_at,
                         version)
VALUES
    ('ASM-0001', 'INC-0001', 5, 4, 4, 4, 88, 'P1', 'P1', 'coordinator',
     '2026-10-12 09:25:00', 1);

INSERT INTO assignments (assignment_id, incident_id, department,
                         requested_action, urgency, target_response_by,
                         status, created_at, responded_at,
                         estimated_arrival, delivery_attempts)
VALUES
    ('ASG-0001', 'INC-0001', 'FIRE_AND_EMERGENCY',
     'Suppress the fire and secure the gas cylinders', 'Immediate',
     '2026-10-12 09:45:00', 'ACCEPTED', '2026-10-12 09:27:00',
     '2026-10-12 09:30:00', '2026-10-12 09:41:00', 1);

INSERT INTO allocations (allocation_id, assignment_id, resource_name,
                         department, quantity, available_from, available_to,
                         staging_point)
VALUES
    ('ALC-0001', 'ASG-0001', 'Pumper 12', 'FIRE_AND_EMERGENCY', 1,
     '2026-10-12 09:30:00', '2026-10-12 13:30:00', 'Dock Road car park');

INSERT INTO field_updates (update_id, incident_id, submitted_by,
                           submitted_at, progress_note, observed_impact,
                           resource_needs, next_review_at,
                           incident_version_seen)
VALUES
    ('UPD-0001', 'INC-0001', 'responder', '2026-10-12 09:55:00',
     'Crews on scene, fire contained to the north wing',
     'Roof partly collapsed', 'Second pumper on standby',
     '2026-10-12 10:30:00', 2);

INSERT INTO audit_events (event_id, actor, action, entity_type, entity_id,
                          detail, occurred_at)
VALUES
    ('AUD-00001', 'coordinator', 'DECIDE_REPORT', 'DisasterReport',
     'RPT-0001', 'VALIDATED', '2026-10-12 09:18:00'),
    ('AUD-00002', 'coordinator', 'CREATE_INCIDENT', 'Incident', 'INC-0001',
     'From report RPT-0001', '2026-10-12 09:20:00'),
    ('AUD-00003', 'coordinator', 'CONFIRM_PRIORITY', 'Incident', 'INC-0001',
     'Confirmed P1', '2026-10-12 09:25:00'),
    ('AUD-00004', 'liaison', 'RESPOND_ASSIGNMENT', 'Assignment', 'ASG-0001',
     'ACCEPTED', '2026-10-12 09:30:00');
