select
    r.session_id,
    n.symbol,
    n.premium_text,
    n.premium_numeric,
    n.conditions,
    r.observed_via,
    n.captured_at_utc
from raw_browser_events r
join normalized_options_flow_events n
    on n.raw_event_id = r.id
where n.conditions ilike '%synthetic%'
order by n.captured_at_utc desc
limit 20;
