'use client';

import { useState } from 'react';
import Link from 'next/link';
import { notify } from '@/shared/notify';
import {
  memberApi,
  MemberNotificationPurpose,
  MemberNotificationSettings,
  MemberNotificationStatus,
} from '@/entities/member';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Label,
  RadioGroup,
  RadioGroupItem,
  RegionError,
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

  const choose = async (purpose: MemberNotificationPurpose, status: 'ALLOWED' | 'DENIED') => {
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
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">通知の設定</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        ログインに使うメールアドレスへ届くお知らせを選べます。選ぶまでは、ご利用に関するお知らせはメールアドレスの確認が済んでからお送りし、キャンペーン・おすすめのご案内はお送りしません。
      </p>
      {isLoading ? (
        <p className="mt-4 text-sm text-muted-foreground">読み込み中...</p>
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
                <CardTitle id={titleId} role="heading" aria-level={2}>
                  {title}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">{description}</p>
                {current === 'UNKNOWN' && (
                  <p className="text-sm text-foreground">まだ選択されていません。</p>
                )}
                <RadioGroup
                  aria-labelledby={titleId}
                  value={current === 'UNKNOWN' ? '' : current}
                  onValueChange={value => void choose(purpose, value as 'ALLOWED' | 'DENIED')}
                  disabled={saving}
                >
                  <Label className="flex items-center gap-2 font-normal">
                    <RadioGroupItem value="ALLOWED" />
                    受け取る
                  </Label>
                  <Label className="flex items-center gap-2 font-normal">
                    <RadioGroupItem value="DENIED" />
                    受け取らない
                  </Label>
                </RadioGroup>
              </CardContent>
            </Card>
          );
        })
      )}
      <Button render={<Link href="/member/" />} variant="outline" className="mt-6 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
