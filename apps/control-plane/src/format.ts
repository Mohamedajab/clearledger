export function minorUnits(value: string): number {
  if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error("Enter a positive amount with at most two decimal places.");
  const [whole, fraction = ""] = value.split(".");
  const result = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(result) || result <= 0) throw new Error("Enter a valid positive amount.");
  return result;
}

export function csvText(headers: string[], rows: (string | number)[][]): string {
  const cell = (value: string | number) => {
    const text = String(value);
    const safe = typeof value === "string" && /^[\s]*[=+@-]/.test(text) ? "'" + text : text;
    return '"' + safe.replaceAll('"', '""') + '"';
  };
  return [headers, ...rows].map(row => row.map(cell).join(",")).join("\r\n") + "\r\n";
}

export function downloadCsv(filename: string, headers: string[], rows: (string | number)[][]) {
  const url = URL.createObjectURL(new Blob([csvText(headers, rows)], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
