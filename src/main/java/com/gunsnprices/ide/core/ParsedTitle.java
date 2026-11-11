package com.gunsnprices.ide.core;

import com.gunsnprices.ParseResult;

/**
 * One parsed title.
 *
 * @param line   one-based line number in the source, or 0 when it does not matter
 * @param title  the text that was parsed (without the price)
 * @param price  the price found with the title, or null
 * @param result what the parser read from the title
 */
public record ParsedTitle(int line, String title, Double price, ParseResult result) {
}
