import { describe, expect, it } from "vitest";
import { csvText, minorUnits } from "./format";

describe("payment amounts", () => {
  it("converts decimal pounds without rounding fractional pence", () => {
    expect(minorUnits("0.29")).toBe(29);
    expect(minorUnits("125.1")).toBe(12510);
    for (const amount of ["0", "-1", "1.005", "Infinity", "1e3", "9007199254740992"]) expect(() => minorUnits(amount)).toThrow();
  });
});
it("escapes journal CSV cells and neutralizes spreadsheet formulas", () => {
  expect(csvText(["reference", "amount"], [['=SUM(A1:A2)', 125], ['a,"b"', 29]])).toBe('"reference","amount"\r\n"\'=SUM(A1:A2)","125"\r\n"a,""b""","29"\r\n');
});
