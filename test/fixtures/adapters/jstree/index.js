import { helper } from './lib/util.js';
import fs from "node:fs";

/**
 * Add two numbers.
 *
 * @param {number} a
 * @param {number} b
 */
export function add(a, b = 1) {
  function hidden() {}
  return a + b;
}

// not a doc comment
function plain() {}

/** Async generator. */
async function* stream(source) {}

/**
 * A shape.
 */
export class Shape extends Base {
  /** Builds it. */
  constructor(name) { super(); }

  /** Area of the shape. */
  area() { return 0; }

  static create() {}

  async load() {}
}

/** Arrow binding. */
const double = (x) => x * 2;

const expr = function (a, b) { return a; };

let counter = 0;

if (counter) {
  function conditional() {}
}
