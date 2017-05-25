# erratic statesman

All functionality is currently REPL-only.

## Environment

Uses `https://github.com/weavejester/environ` under the hood, so follow the
instructions there if you're having trouble setting environment variables.

`:jira-host` - host for Jira instance, e.g. `"jira.atlassian.com"`
`:jira-user` - username for Jira access
`:jira-password` - password for Jira access

`:toggl-token` - API token for Toggl
`:toggl-user-agent` - Your email address
`:toggl-workspace-id` - Numeric ID, can be found in most Toggl URLs

`:toggl2jira-client-id` - The client ID in Toggle to reconcile against Jira

## Reconcile Toggl -> Jira

Scans Toggl and Jira then throws exceptions for any time/worklog data that
appears to be inconsistent or missing.

- Uses Toggl as source of truth
- Ensures that all Toggl time tracked is logged in Jira once, and only once
- Ensures that all Jira times from issues setup in Toggl map back to Toggl
- Provides recommended Jira worklog summaries for new Toggl times
- Ensures that Jira worklogs match the mapped Toggl times to the closest minute
- Uses simple EDN vectors to map IDs between Toggl Jira

### Usage

Ensure that all project names in Toggl contain the relevant Jira issue key in
an EDN vector.

Jira issue keys appear in the URL as `https://xxx/browse/<project-key>`.

If the Jira URL for an issue was `browse/SALES-123` then the project in Toggl
should include the string `[SALES-123]` or `["SALES-123"]`.

Note that technically the former example maps to the _symbol_ `SALES-123`, not
the string `"SALES-123"`, but everything parsed is mapped to a string with `str`
before use anyway.

0. Fire up a REPL
0. `toggl2jira.core/do-it!`

Anything else outstanding will trigger an exception that is _hopefully_ self
explanatory.

If you already have some data in Jira, the scanner will probably complain that
the worklogs don't reference anything in Toggl. Adding a simple EDN vector of
Toggl IDs to the description will link that Jira worklog to those Toggl times.

The scanner will show a list of Toggl data for entries from the same date as the
Jira worklog, so hopefully you don't have to search to hard to find the right ID
values.

e.g. assuming you have a Jira worklog with the description "Doing some work" and
Toggl times with IDs for this work, 123, 456 and 789. Simply change the Jira
description to "Doing some work [123 456 789]" and the scanner should be happy.

## Development

2 boot tasks are provided for convenience with hot-reloaded REPL code,
`boot repl-server` and `boot repl-client`. Obviously (hopefully) you should make
sure the server process is up and running before starting the client.
