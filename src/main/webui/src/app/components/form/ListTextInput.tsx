import { TextInput } from '@carbon/react';
import { useState } from 'react';

const parseList = (text: string): string[] =>
  text
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);

interface ListTextInputProps {
  id: string;
  labelText: string;
  placeholder?: string;
  helperText?: string;
  value: string[];
  onChange: (value: string[]) => void;
  onBlur?: () => void;
  invalid?: boolean;
  invalidText?: string;
}

/**
 * Edits a list of values as comma-separated text.
 * <p>
 * The text being typed is held here and only the parsed list is published, so separators and spacing survive
 * editing: re-deriving the text from the list on every keystroke makes a separator impossible to delete and
 * moves the caret to the end. Blur normalizes the text to the canonical form.
 */
export const ListTextInput = ({ id, labelText, placeholder, helperText, value, onChange, onBlur, invalid, invalidText }: ListTextInputProps) => {
  const [text, setText] = useState(() => value.join(', '));
  const [publishedValue, setPublishedValue] = useState(value);

  // adopt a list set from the outside (a form reset, or an existing entity being loaded)
  if (value !== publishedValue) {
    setPublishedValue(value);
    if (parseList(text).join(',') !== value.join(',')) {
      setText(value.join(', '));
    }
  }

  return (
    <TextInput
      id={id}
      labelText={labelText}
      placeholder={placeholder}
      helperText={helperText}
      value={text}
      onChange={(e) => {
        setText(e.target.value);
        const parsed = parseList(e.target.value);
        setPublishedValue(parsed);
        onChange(parsed);
      }}
      onBlur={() => {
        setText(parseList(text).join(', '));
        onBlur?.();
      }}
      invalid={invalid}
      invalidText={invalidText}
    />
  );
};
