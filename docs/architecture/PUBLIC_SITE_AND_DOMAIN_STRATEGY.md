# Public Site And Domain Strategy

## Decision

`archdox.co.kr` should become the public product entry point, not the default
logged-in SaaS application screen.

ArchDox now has two product surfaces:

```text
ArchDox SaaS
  Full architecture-office workflow app.

ArchDox Engine / MCP Gateway
  External review/legal/checklist engine that customers can connect to their
  own AI agents.
```

The public root site should explain and sell the Engine/MCP product direction,
then route signed-in users to the appropriate app surface.

The root landing page should feel like a simple, technical product page rather
than a conventional architecture-office dashboard. Its first viewport should use
the `archdoX` brand, a compact top navigation, a clear login/try-free path, and
a code-oriented hero that makes the Engine/MCP value understandable at a glance:
customers connect their AI agent or internal system, submit structured review
context, and receive source-backed findings/actions. The public landing page is
allowed to be visually more expressive than the operational SaaS UI, but it
must still stay restrained, readable, and mobile-safe. It should reuse the
ArchDox product identity: deep green/blue-green surfaces, white panels, muted
green-gray text, and restrained mustard highlights. Avoid copying another
vendor's distinctive trade dress such as matching hero gradients, top-strip
colors, logo geometry, or code-window composition too closely.

## Recommended Domain Layout

| Host | Purpose |
| --- | --- |
| `archdox.co.kr` | Public product site, signup, pricing, "Connect my AI" entry. |
| `www.archdox.co.kr` | Redirect to `archdox.co.kr`. |
| `app.archdox.co.kr` | Existing ArchDox SaaS user app: project, site, report, document workflow. |
| `admin.archdox.co.kr` | Office admin and platform admin app. |
| `api.archdox.co.kr` | Cloud API REST/WebSocket origin, unless hidden behind same-origin proxy. |
| `mcp.archdox.co.kr` | Future MCP Gateway endpoint. |
| `connect.archdox.co.kr` | Optional Agent Connect approval/onboarding host. Can start as `archdox.co.kr/connect`. |
| `docs.archdox.co.kr` | Optional public developer/MCP documentation later. |

Public landing CTAs should link to explicit SaaS auth routes:

```text
https://app.archdox.co.kr/signup
https://app.archdox.co.kr/login
```

The React app owns those paths as SPA routes and opens the matching login/signup
mode on the shared `AuthScreen`. Avoid linking the public landing to the bare
SaaS root when the user intent is signup or login.

Until the MCP Gateway is enabled, `mcp.archdox.co.kr` should not expose a fake
MCP endpoint. It should redirect to the public MCP developer documentation so
external users do not mistake the placeholder for a working protocol endpoint.

## Why `api.archdox.co.kr` Exists

`api.archdox.co.kr` is not required just to make the browser app work. Browser
apps can call the Cloud API through same-origin reverse proxy paths such as:

```text
https://app.archdox.co.kr/api/...
https://admin.archdox.co.kr/api/...
```

The dedicated API host exists for product and operations boundaries:

```text
external ArchDox Engine API customers
future MCP Gateway or Agent Connect bootstrap flows
mobile apps
partner/customer system integrations
health checks and API-specific monitoring
API-specific WAF/rate-limit/body-size policies
clear separation from public product pages
```

So the rule is:

```text
Browser apps may use same-origin /api for simplicity.
External clients should use api.archdox.co.kr.
MCP should eventually use mcp.archdox.co.kr, not the public web root.
```

In MVP, `api.archdox.co.kr` can still point to the same Lightsail instance and
the same Cloud API process. It is a boundary name, not proof that a separate
server already exists.

Initial MVP can keep fewer hosts:

```text
archdox.co.kr        -> public site
app.archdox.co.kr    -> SaaS app
admin.archdox.co.kr  -> admin app
api.archdox.co.kr    -> API
mcp.archdox.co.kr    -> future MCP
```

## Public Site Experience

The public root site should focus on the external product:

```text
ArchDox Engine
Architecture document, supervision, checklist, and law-review engine for AI agents.
```

Primary flows:

```text
visit archdox.co.kr
  -> learn what ArchDox Engine does
  -> sign up / log in
  -> choose plan
  -> click "Connect my AI"
  -> choose Agent host: ChatGPT, Claude, Cursor, Codex, Custom Agent
  -> approve scopes/tool packs
  -> receive MCP connection instructions or automatic connect flow
```

Public pages:

```text
/
/pricing
/signup
/login
/connect
/connect/approve/{connectionId}
/developers
/developers/mcp
/legal-updates
```

The SaaS workflow app should not be the first screen of the root domain.

## Existing SaaS App Relocation

The current user-facing app should move to:

```text
app.archdox.co.kr
```

It remains the place for:

```text
projects
sites
reports
photos
preflight review
signature
document generation
document download
worker chat for report tasks
```

The admin app should move to:

```text
admin.archdox.co.kr
```

It remains the place for:

```text
office member and permission management
templates
AI provider policy
platform ops
legal sync/admin
agent status
```

## Agent Connect UX

The customer-facing AI connection flow should be simple:

```text
1. ArchDox signup
2. plan/payment setup
3. click "Connect my AI"
4. choose Agent host
   - ChatGPT
   - Claude
   - Cursor
   - Codex
   - Custom Agent
5. approve requested tool packs/scopes
6. connection is ready
```

After that, the customer can ask their agent:

```text
ArchDox로 이 감리일지 검토해줘.
```

The agent then uses ArchDox MCP tools:

```text
create_review_session
submit_document
submit_context_facts
normalize_context
run_validation
get_review_result
```

## Routing And Deployment Direction

At the edge layer, Nginx/Cloudflare/ALB should route by host:

```text
archdox.co.kr        -> public-site frontend
app.archdox.co.kr    -> client/web frontend
admin.archdox.co.kr  -> admin frontend
api.archdox.co.kr    -> cloud-api
mcp.archdox.co.kr    -> MCP Gateway, later
```

During MVP, this can still run on one Lightsail instance. Host-based routing is
enough. Separate instances/services are not required until traffic or security
needs justify it.

## Current MVP Implementation

The initial implementation uses one web container with host-based static app
routing:

```text
public-site       -> /usr/share/nginx/html/public
client/web dist   -> /usr/share/nginx/html/client
admin dist        -> /usr/share/nginx/html/admin
```

Current container routing:

```text
archdox.co.kr        -> public-site
www.archdox.co.kr    -> redirect to archdox.co.kr
app.archdox.co.kr    -> client/web
admin.archdox.co.kr  -> admin
api.archdox.co.kr    -> Cloud API
mcp.archdox.co.kr    -> placeholder 404 until MCP Gateway is implemented
```

The public site V1 static pages live in:

```text
public-site/
public-site/connect/
public-site/developers/mcp/
public-site/legal-updates/
```

It is intentionally static for now. The V1 pages explain the product position,
Agent Connect direction, MCP developer shape, and legal-update digest direction.
Agent Connect, signup, payment, OAuth approval, and live MCP configuration
screens should be implemented after Engine API authentication, quota, tool-pack
scopes, and review sessions are stable.

## API Origin Policy

Two deployment options are acceptable.

### Option A: Dedicated API Host

```text
client app -> https://api.archdox.co.kr/api/v1/...
```

Pros:

```text
clear separation
easy to route SaaS/admin/MCP separately
```

Cons:

```text
CORS and cookie/token configuration must be correct
```

### Option B: Same-Origin Reverse Proxy

```text
app.archdox.co.kr/api/v1/... -> reverse proxy -> cloud-api
admin.archdox.co.kr/api/v1/... -> reverse proxy -> cloud-api
```

Pros:

```text
simpler browser security and fewer CORS surprises
```

Cons:

```text
more edge routing rules
```

For early MVP, same-origin reverse proxy is often simpler for the browser apps.
For MCP and external Engine API, a dedicated host such as `api.archdox.co.kr`
or `mcp.archdox.co.kr` is clearer.

## Migration Plan

### Phase D-1: Document Domain Strategy

Status: current.

Define public site, app, admin, api, and mcp host responsibilities.

### Phase D-2: Prepare App For Subdomain Routing

Make sure frontend configuration can use:

```text
VITE_API_BASE_URL
APP_PUBLIC_BASE_URL
ADMIN_PUBLIC_BASE_URL
```

The app should not assume it always runs at `archdox.co.kr`.

### Phase D-3: Public Landing Skeleton

Add a small public site or landing route for:

```text
ArchDox Engine
pricing/signup/login
Connect my AI
developer/MCP overview
```

This can be a separate frontend project later. For MVP it can be a simple static
site or a Vite app.

### Phase D-4: Agent Connect UI

Build:

```text
/connect
/connect/approve/{connectionId}
tool-pack/scope approval screen
connection status screen
```

This UI should call Agent Connect Bootstrap APIs, not manually expose tokens.

### Phase D-5: DNS And Edge Routing

Configure DNS and edge proxy:

```text
archdox.co.kr
app.archdox.co.kr
admin.archdox.co.kr
api.archdox.co.kr
mcp.archdox.co.kr
```

Do not move production traffic until smoke tests pass on the new hosts.

### Phase D-6: Root Domain Cutover

Change `archdox.co.kr` from SaaS app to public site.

The old app URL should redirect or link clearly to:

```text
app.archdox.co.kr
```

## Non-Negotiable Rules

```text
Root domain is product/onboarding, not the logged-in work app.
Existing SaaS app should move to app.archdox.co.kr.
Admin app should not be mixed with the public site.
MCP Gateway should have a dedicated host when public.
Agent Connect approval must not expose unrestricted tokens.
Host-based routing is enough for MVP; separate servers can wait.
```
