package ink.doa.workbench.security.identity.auth

fun normalizeSubject(subject: String): String = subject.trim().lowercase()
