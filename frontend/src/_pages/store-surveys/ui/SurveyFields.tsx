import { Control, FieldPath, FieldValues } from 'react-hook-form';
import {
  Button,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

interface FieldProps<T extends FieldValues> {
  control: Control<T>;
  name: FieldPath<T>;
  label: string;
  required?: boolean;
  validate: (value: string) => true | string;
}
export function SurveyTextField<T extends FieldValues>({
  control,
  name,
  label,
  required,
  validate,
  multiline,
  maxLength,
  type,
}: FieldProps<T> & { multiline?: boolean; maxLength?: number; type?: 'datetime-local' }) {
  return (
    <FormField
      control={control}
      name={name}
      rules={{ validate }}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            {multiline ? (
              <Textarea
                {...field}
                value={field.value ?? ''}
                required={required}
                maxLength={maxLength}
              />
            ) : (
              <Input
                {...field}
                value={field.value ?? ''}
                required={required}
                maxLength={maxLength}
                type={type}
              />
            )}
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
export function SurveyChoiceField<T extends FieldValues>({
  control,
  name,
  label,
  required,
  validate,
  items,
  disabled,
  onChange,
}: FieldProps<T> & {
  items: { value: string; label: string }[];
  disabled?: boolean;
  onChange?: (value: string) => void;
}) {
  return (
    <FormField
      control={control}
      name={name}
      rules={{ validate }}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <Select
            name={field.name}
            value={field.value || null}
            items={items}
            required={required}
            disabled={disabled}
            onValueChange={value => {
              field.onChange(value ?? '');
              onChange?.(value ?? '');
            }}
          >
            <FormControl>
              <SelectTrigger
                ref={field.ref}
                onBlur={field.onBlur}
                className="w-full min-w-0 max-w-full"
              >
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {items.map(({ value, label }) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FormMessage />
          {!required && field.value && (
            <Button
              type="button"
              variant="ghost"
              disabled={disabled}
              onClick={() => field.onChange('')}
            >
              選択を解除
            </Button>
          )}
        </FormItem>
      )}
    />
  );
}
