package com.joz.context.skill;

/** A loaded skill — parsed from SKILL.md files. */
public record Skill(String name, String description, String instructions) {}
