# Security Policy

This document describes how to report security vulnerabilities in this
project, what information to include, and how we will handle and disclose
issues. If you discover a potential security problem, please follow the
instructions below so we can respond quickly and responsibly.

## Supported Versions

This repository contains two main deliverables:

- Backend (Java Spring Boot): version declared in `backend/build.gradle`
- Frontend (Next.js): version declared in `frontend/package.json`

Current versions in this workspace (source of truth):

| Component | File | Version |
| --------- | ---- | ------- |
| Backend   | `backend/build.gradle` | `0.0.1-SNAPSHOT` |
| Frontend  | `frontend/package.json` | `0.1.0` |

Supported versions policy (recommended):

- We support the latest released minor version for each component. For
	pre-1.0 projects, each minor (0.x) release is treated as a supported
	release line while it remains the latest published minor version.
- We also provide limited support for the `master` branch snapshots for
	active development, but critical security fixes are expected to be
	released as tagged releases (not only snapshots).

If you are unsure which version you are running, check the `backend/build.gradle`
(`version = '...'`) and `frontend/package.json` (`version` field), or use
application metadata emitted at runtime (for example, the Spring Boot
`Build-Info` or an endpoint that surfaces the frontend version).

## Reporting a Vulnerability

Preferred private reporting channels (choose one):

- Create a private GitHub Security Advisory: https://github.com/<owner>/<repo>/security/advisories
- Email: security@your-domain.example (PGP key: replace-with-base64-or-url)

Replace the placeholders above with the repository owner and a working
contact email. Using GitHub Security Advisories is preferred because it
supports coordinated disclosure and CVE requests.

When reporting, please include as much of the following information as is
safe to share:

- Affected version(s) / commit SHA
- A concise summary of the issue and impact
- Step-by-step reproduction instructions or a proof-of-concept (PoC)
- Expected vs actual behavior, logs, and error messages
- Network/service configuration or minimal test environment details
- CVE requests or known exploitation in the wild (if applicable)

Do not publicly disclose the vulnerability until a patch or mitigation is
available, or we have agreed to public disclosure.

## How we will respond

We commit to the following initial service-levels once a valid report is
received through one of the preferred channels:

- Acknowledge receipt within 3 business days
- Triage and initial severity assessment within 7 calendar days
- Provide regular updates (at least weekly) until a fix or mitigation is
	available
- Work with reporters on applying patches, CVE assignment, and advisory
	text

High-severity issues will be prioritized. Timelines for fixes may vary by
severity and complexity; we aim to release patches for critical issues as
quickly as possible and will coordinate embargoed disclosure when needed.

## Disclosure and Coordinated Release

We follow coordinated (responsible) disclosure:

- Maintain confidentiality while investigating and preparing fixes
- Coordinate release of fixes and public advisories with the reporter
- Assign CVEs for applicable issues (we may request the reporter's
	assistance to file a CVE or request one on behalf of the project)
- Typical private disclosure window: 90 days from the initial report for
	non-critical issues. Critical, actively exploited vulnerabilities may
	require faster action and public advisories may be released sooner in
	coordination with upstream partners.

If you believe a shorter or longer timeline is needed (for example, if
the issue is already being exploited), please indicate that in the report
and we will adjust priorities accordingly.

## Report Template (copy when you file)

Title: Short descriptive title

Product: Kizuna (repository: github.com/kanghouchao/Kizuna)

Versions: e.g. 5.1.0, commit abcdef123

Environment: e.g. Java 21, PostgreSQL 13, Docker Compose

Description: One-paragraph description of the issue and impact.

Reproduction steps / PoC: Exact commands, HTTP requests, or code showing
how to reproduce. Prefer a minimal PoC with clear instructions.

Mitigations: Any temporary mitigation steps (if known).

Attachments: Logs, screenshots, PoC code (attach in a password-protected
archive if necessary).

## Safe Harbor for Security Researchers

We appreciate responsible disclosures. If you follow this policy and act
in good faith to avoid privacy invasions or service disruption, we will
not pursue legal action against you for your good-faith security
research. This is not legal advice — consult a lawyer if you need one.

## Credits and Rewards

We will acknowledge researchers who report security issues in our
advisories unless they request anonymity. We do not currently operate a
formal bug bounty program; if that changes we will publish the details.

## How we handle CVEs

- We will coordinate with the reporter to request a CVE when appropriate.
- We may request additional information to support CVE assignment.

## Security contact and PGP key

Preferred contact: kanhouchou@gmail.com

PGP key (optional):

-----BEGIN PGP PUBLIC KEY BLOCK-----
Replace-this-with-your-project-or-maintainer-public-key
-----END PGP PUBLIC KEY BLOCK-----

Replace the contact email and PGP block with the project's actual contact
information before publishing this file.

## Disclosure history

We will publish a short disclosure history for resolved issues in the
project releases or security advisories. This helps users assess risk and
upgrade accordingly.

## External resources and references

- CVE: https://cve.mitre.org/
- CWE: https://cwe.mitre.org/
- GitHub security advisories: https://docs.github.com/en/code-security/security-advisories

---

If you'd like, I can also:

1) Replace placeholder contact details with a suggested address derived
	 from the repository owner (example: security+my@kanghouchao.dev).
2) Open a PR with this change (or create a draft PR for review).

Please tell me which email and PGP key (if any) you'd like to publish,
or confirm that placeholders are acceptable.
