declare module "tweetnacl-util" {
  export function encodeBase64(array: Uint8Array): string;
  export function decodeBase64(value: string): Uint8Array;
}
