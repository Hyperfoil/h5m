import type { ZodType } from 'zod';

export const fieldError = (errors: unknown[]): string | undefined => {
  const e = errors[0];
  if (!e) return undefined;
  if (typeof e === 'string') return e;
  if (typeof e === 'object' && 'message' in e) return (e as { message: string }).message;
  return undefined;
};

export const groupValidator = (schema: ZodType) => ({ value }: { value: unknown }) => {
  const result = schema.safeParse(value);
  if (!result.success) {
    const fields: Record<string, string> = {};
    for (const issue of result.error.issues) {
      // a list is edited as a single input, so an issue on one of its elements belongs to the property
      const key = issue.path.filter((p) => typeof p !== 'number').join('.');
      if (key && !fields[key]) fields[key] = issue.message;
    }
    return { fields };
  }
  return undefined;
};
