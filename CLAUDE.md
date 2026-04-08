# gstack

Use the `/browse` skill from gstack for all web browsing. Never use `mcp__claude-in-chrome__*` tools.

Available gstack skills:
- `/office-hours` — structured async feedback sessions
- `/plan-ceo-review` — prepare plan for CEO review
- `/plan-eng-review` — prepare plan for engineering review
- `/plan-design-review` — prepare plan for design review
- `/design-consultation` — get design guidance
- `/review` — code review
- `/ship` — ship a feature end-to-end
- `/land-and-deploy` — land and deploy changes
- `/canary` — canary deployment
- `/benchmark` — run benchmarks
- `/browse` — web browsing (use this for ALL web browsing)
- `/qa` — full QA pass
- `/qa-only` — QA without deploy
- `/design-review` — design review
- `/setup-browser-cookies` — configure browser cookies
- `/setup-deploy` — configure deployment
- `/retro` — retrospective
- `/investigate` — investigate an issue
- `/document-release` — document a release
- `/codex` — codex tasks
- `/cso` — CSO review
- `/autoplan` — auto-generate a plan
- `/careful` — careful/cautious mode
- `/freeze` — freeze changes
- `/guard` — guard mode
- `/unfreeze` — unfreeze changes
- `/gstack-upgrade` — upgrade gstack

## Skill routing

When the user's request matches an available skill, ALWAYS invoke it using the Skill
tool as your FIRST action. Do NOT answer directly, do NOT use other tools first.
The skill has specialized workflows that produce better results than ad-hoc answers.

Key routing rules:
- Product ideas, "is this worth building", brainstorming → invoke office-hours
- Bugs, errors, "why is this broken", 500 errors → invoke investigate
- Ship, deploy, push, create PR → invoke ship
- QA, test the site, find bugs → invoke qa
- Code review, check my diff → invoke review
- Update docs after shipping → invoke document-release
- Weekly retro → invoke retro
- Design system, brand → invoke design-consultation
- Visual audit, design polish → invoke design-review
- Architecture review → invoke plan-eng-review
- Save progress, checkpoint, resume → invoke checkpoint
- Code quality, health check → invoke health
