'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import {
  memberApi,
  MemberNotificationPurpose,
  MemberNotificationSettings,
  MemberNotificationStatus,
} from '@/entities/member';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Label,
  RadioGroup,
  RadioGroupItem,
  RegionError,
  Skeleton,
} from '@/shared/ui';

const PURPOSES: {
  purpose: MemberNotificationPurpose;
  title: string;
  description: string;
  status: (settings: MemberNotificationSettings) => MemberNotificationStatus;
}[] = [
  {
    purpose: 'BUSINESS',
    title: 'ご利用に関するお知らせ',
    description: 'ポイントの有効期限など、ご利用中のサービスに関わるお知らせです。',
    status: settings => settings.business_status,
  },
  {
    purpose: 'MARKETING',
    title: 'キャンペーン・おすすめのご案内',
    description: 'キャンペーンや新しいサービスのご案内です。',
    status: settings => settings.marketing_status,
  },
];

const CHOICES = [
  { value: 'ALLOWED', label: '受け取る' },
  { value: 'DENIED', label: '受け取らない' },
] as const;

type Choice = (typeof CHOICES)[number]['value'];

/**
 * 会員ポータルの通知設定。用途ごとに受け取る・受け取らないを選び、選んだ時点で保存する。
 * 未確認の用途にはどちらの選択も付けない。選ぶまでの扱い（業務はメールアドレスの確認後に送る、販促は送らない）を本文で示す。
 * 保存の失敗では選択が保存済みの値へ戻り、押した選択が画面から消えるので warning で失敗を伝える。
 */
export function MemberNotificationSettingsPage() {
  const { data, setData, isLoading, failure, reload } = useResource(() =>
    memberApi.notificationSettings()
  );
  const [saving, setSaving] = useState(false);

  const choose = async (purpose: MemberNotificationPurpose, status: Choice) => {
    setSaving(true);
    try {
      setData(await memberApi.chooseNotification(purpose, status));
      notify.success('通知の設定を保存しました');
    } catch (error) {
      notify.warning(getApiErrorMessage(error, '通知の設定を保存できませんでした'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <p className="text-muted-foreground">
        ログインに使うメールアドレスへ届くお知らせを選べます。選ぶまでは、ご利用に関するお知らせはメールアドレスの確認が済んでからお送りし、キャンペーン・おすすめのご案内はお送りしません。
      </p>
      {isLoading ? (
        <div role="status" aria-label="読み込み中" className="mt-4 space-y-4">
          {PURPOSES.map(({ purpose }) => (
            <Skeleton key={purpose} className="h-48 w-full rounded-xl" />
          ))}
        </div>
      ) : failure !== null || data === null ? (
        <RegionError
          className="mt-4"
          message="通知の設定を取得できませんでした"
          onRetry={() => void reload()}
        />
      ) : (
        PURPOSES.map(({ purpose, title, description, status }) => {
          const current = status(data);
          const titleId = `notification-${purpose}`;
          return (
            <Card key={purpose} className="mt-4">
              <CardHeader>
                <CardTitle id={titleId} role="heading" aria-level={2} className="text-lg">
                  {title}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-muted-foreground">{description}</p>
                {current === 'UNKNOWN' && <p>まだ選択されていません。</p>}
                <RadioGroup
                  aria-labelledby={titleId}
                  value={current === 'UNKNOWN' ? '' : current}
                  onValueChange={value => void choose(purpose, value as Choice)}
                  disabled={saving}
                  className="gap-0"
                >
                  {CHOICES.map(({ value, label }) => (
                    <Label
                      key={value}
                      className="flex min-h-12 items-center gap-3 text-lg font-normal"
                    >
                      <RadioGroupItem value={value} className="size-6 [&_svg]:size-3" />
                      {label}
                    </Label>
                  ))}
                </RadioGroup>
              </CardContent>
            </Card>
          );
        })
      )}
    </div>
  );
}
