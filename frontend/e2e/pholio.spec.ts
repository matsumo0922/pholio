import { expect, test } from '@playwright/test';

test('photo grid, album creation, exclude, and restore flow', async ({ page }) => {
  await page.goto('/home');

  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible();
  await expect(page.getByAltText('sample.png')).toBeVisible();

  await page.getByRole('button', { name: 'アルバム' }).click();
  await page.getByLabel('新しいアルバム').fill('E2E Album');
  await page.getByRole('button', { name: '作成' }).click();
  await expect(page.getByText('E2E Album')).toBeVisible();

  await page.getByRole('button', { name: 'ホーム' }).click();
  await page.getByTestId('select-photo').first().click();
  await page.getByRole('button', { name: 'ライブラリから除外' }).click();
  await expect(page.getByText('写真がありません')).toBeVisible();

  await page.getByRole('button', { name: '設定' }).click();
  await expect(page.getByRole('heading', { name: '設定' })).toBeVisible();
  await expect(page.getByAltText('sample.png')).toBeVisible();
  await page.getByRole('button', { name: '復元' }).click();

  await page.getByRole('button', { name: 'ホーム' }).click();
  await expect(page.getByAltText('sample.png')).toBeVisible();
});
