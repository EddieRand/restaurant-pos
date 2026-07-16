import { useEffect, useState } from 'react'
import { groupBuyingApi, type GroupBuyingRedemption } from '../api/groupBuying'

const money = (minor: number) => `¥${(minor / 100).toFixed(2)}`

export default function GroupBuyingRedemptionsPage() {
  const [rows, setRows] = useState<GroupBuyingRedemption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    try {
      setError('')
      setRows(await groupBuyingApi.listRedemptions())
    } catch {
      setError('无法加载团购券核销记录，请检查服务端连接。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load()
    }, 5_000)
    return () => window.clearInterval(timer)
  }, [])

  const total = rows.filter(row => row.status === 'SUCCEEDED').reduce((sum, row) => sum + row.redeemedAmountMinorUnit, 0)
  const douyin = rows.filter(row => row.provider === 'DOUYIN').length
  const meituan = rows.filter(row => row.provider === 'MEITUAN').length

  return (
    <div className="p-4 md:p-6">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-500">Local Services</p>
          <h1 className="mt-1 text-2xl font-bold text-gray-900">抖音 / 美团团购核销</h1>
          <p className="mt-1 text-sm text-gray-500">查看到店验券、履约核销、关联订单和平台流水。</p>
        </div>
        <button type="button" className="btn-secondary" onClick={() => void load()}>刷新</button>
      </div>

      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <Stat label="成功核销" value={`${rows.length} 笔`} />
        <Stat label="核销金额" value={money(total)} />
        <Stat label="平台分布" value={`抖音 ${douyin} · 美团 ${meituan}`} />
      </div>

      {error && <div role="alert" className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="overflow-hidden rounded-2xl border border-gray-100 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-4 py-3">
          <h2 className="font-semibold text-gray-900">核销流水</h2>
          <p className="text-xs text-gray-400">券码只展示后四位；Demo 测试券会明确标识。</p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-[880px] w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs text-gray-500">
              <tr>
                <th className="px-4 py-3">平台 / 套餐</th><th className="px-4 py-3">券码</th>
                <th className="px-4 py-3">关联订单</th><th className="px-4 py-3">核销金额</th>
                <th className="px-4 py-3">状态</th><th className="px-4 py-3">核销时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && <tr><td colSpan={6} className="px-4 py-10 text-center text-gray-400">加载中…</td></tr>}
              {!loading && rows.length === 0 && <tr><td colSpan={6} className="px-4 py-10 text-center text-gray-400">暂无核销记录，请在 Cashier 结账页完成一次团购券核销。</td></tr>}
              {rows.map(row => (
                <tr key={row.id} className="hover:bg-gray-50/60">
                  <td className="px-4 py-3"><div className="flex items-center gap-2"><span className={`badge ${row.provider === 'DOUYIN' ? 'bg-gray-900 text-white' : 'bg-yellow-100 text-yellow-800'}`}>{row.provider === 'DOUYIN' ? '抖音' : '美团'}</span>{row.demo && <span className="badge bg-purple-100 text-purple-700">Demo</span>}</div><p className="mt-1 font-medium text-gray-800">{row.title}</p></td>
                  <td className="px-4 py-3 font-mono text-gray-600">{row.maskedCode}</td>
                  <td className="px-4 py-3"><a className="font-mono text-brand-600 hover:underline" href={`/orders?search=${encodeURIComponent(row.orderId)}`}>{row.orderId.slice(0, 8)}…</a><p className="mt-1 text-xs text-gray-400">{row.providerReference}</p></td>
                  <td className="px-4 py-3 font-semibold text-gray-900">{money(row.redeemedAmountMinorUnit)}</td>
                  <td className="px-4 py-3"><span className="badge bg-green-100 text-green-700">已核销</span></td>
                  <td className="px-4 py-3 text-gray-600">{new Date(row.createdAt).toLocaleString('zh-CN')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl border border-gray-100 bg-white p-4 shadow-sm"><p className="text-xs text-gray-500">{label}</p><p className="mt-1 text-xl font-bold text-gray-900">{value}</p></div>
}
