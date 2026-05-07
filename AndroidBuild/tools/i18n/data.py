"""Shared translation registry. Imported by generate_strings and the
per-language translation modules so they all mutate the same dict."""

T = {}
