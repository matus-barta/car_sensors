# Tasks

Work that is understood but not scheduled yet, one file per task. A task that
has been done is deleted, not marked done: this directory only ever holds what
is still open, and git keeps the rest.

## A task file

The file name is the task's id - its title in lowercase, words joined by
hyphens - and the title is what it is called everywhere else.

```markdown
---
title: "Rate limit the upload endpoint"
status: backlog
area: protocol
depends_on: []
---

What the work is, why it is worth doing, and what is still to be decided.
```

| Field | Values |
| --- | --- |
| `title` | The task, as a sentence saying what to do. Quoted. |
| `status` | `backlog` - understood, not started. `next` - the one to pick up next. `in-progress` - being worked on. `blocked` - waiting on something outside the repository; the text says what. |
| `area` | The part of the project it belongs to: `www`, `ingest`, `android`, `protocol`, `device-auth`, `database`, `ci`, `trips`, `geocoding`, `distribution`, `tools`. |
| `depends_on` | Ids of tasks that have to be done first - file names without `.md`. Only what the task's own text says it needs. |

Tasks that are merely related - touching the same code, or better done in a
particular order - are not dependencies. Say so in the text, with a link, in a
closing paragraph that starts **Related.**

## Referring to tasks

By path, from anywhere in the repository: `tasks/rate-limit-the-upload-endpoint.md`.
Inside a task, as a relative link: `[Rate limit the upload endpoint](rate-limit-the-upload-endpoint.md)`.
Never as "the entry below" - the files have no order.
