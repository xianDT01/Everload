# Beta dependency security review

Reviewed 2026-11-18. Reassess before 2026-12-18 and whenever rendering changes.

The first image scan found 44 fixable HIGH/CRITICAL findings. The beta dependency
update uses Spring Boot 3.5.16, Tomcat 10.1.62 and Jackson 2.21.8; it stays on
Spring Framework 6.2 rather than introducing a Spring Boot 4 migration.

The final amd64 beta candidate (source revision
`a35d71317af7d93cf6807dc306fd49240197c9bf`) passed the Trivy 0.69.3 scan on
2026-11-18 with zero unhandled fixable HIGH/CRITICAL findings. The two findings
below remain visible as suppressed findings in the JSON report. This scan uses
`--ignore-unfixed`; it is not a claim of zero vulnerabilities at every severity.

Two Spring MVC findings require application-specific features which are absent:

| Finding | Vendor conditions | EverLoad assessment |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | XsltView, broad view-rendering mapping and an implicit view name | No XSLT views or resolver. SpaController returns the constant explicit view `forward:/index.html`. Other controllers return API data, not server-rendered templates. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Rendering view fragments over Server-Sent Events with attacker-controlled data | No SSE view-fragment rendering, SseEmitter or FragmentsRendering. The client is an Angular SPA. |

This is a **not-applicable assessment of the current application**, not a claim
that Spring 6.2.19 contains the upstream fixes. Other applications using the same
library can be affected. The OSS upstream fixes are in Spring Framework 7.0.9;
6.2.20 is listed by the vendor as enterprise-only.

`.trivyignore.yaml` limits the two findings to the exact Maven package/version and
expires after 30 days. Release scans show suppressed findings alongside their
statements; the other vulnerability checks remain blocking. The regression test
inspects production bytecode for these rendering features and verifies that the
SPA view is explicit. It is a guard against ordinary feature additions, not a
proof against arbitrary dynamically loaded code. No such extension mechanism is
configured in this application.

Remove or reassess the exceptions if templates, XSLT, SSE, plugins, dependency
versions or view routing change. Do not extend expiration without another review.
