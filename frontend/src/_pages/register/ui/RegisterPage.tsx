'use client';

import { Suspense } from 'react';
import { RegisterForm } from '@/features/tenant-register';

export default function RegisterPage() {
  return (
    <Suspense>
      <RegisterForm />
    </Suspense>
  );
}
