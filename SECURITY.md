# Security policy

Twinrail is a local test tool. It holds no money, connects to no payment network and makes no outbound calls at
runtime. Even so, it parses untrusted message content and serves an HTTP API, so security reports are welcome.

## Supported versions

Until v1.0, only the latest release receives fixes.

## Reporting a vulnerability

Please **do not open a public issue** for a vulnerability. Use GitHub's private vulnerability reporting:
open the repository's **Security** tab and choose **Report a vulnerability**. Include what you found, how to
reproduce it and the version you tested.

You can expect an acknowledgement within a week. We will agree a disclosure timeline with you and credit you in the
release notes if you wish.

## Scope

In scope: XML or file parsing flaws (for example entity expansion), injection in the inspector UI or API, unsafe
defaults in the Docker image, and any code path that makes Twinrail call out to the network.

Out of scope: use of Twinrail as a production payment system, which it is not.
