import Hjson from "npm:hjson";

const filePath = new URL("../app/src/main/assets/train_series.hjson", import.meta.url);
const raw = await Deno.readTextFile(filePath);

try {
  const parsed = Hjson.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error("Expected top-level array of train series.");
  }

  console.log(`Hjson valid: ${parsed.length} entries parsed from ${filePath.pathname}`);
} catch (error) {
  console.error(`Invalid Hjson in ${filePath.pathname}`);
  if (error instanceof Error) {
    console.error(error.message);
  } else {
    console.error(String(error));
  }
  Deno.exit(1);
}
