import { useQuery } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '@/lib/api'
import type { PerformanceStat } from '@/lib/types'
import { Card, EmptyState, Loading, PageHeader } from '@/components/ui'

/** A student's own results, against the rest of their cohort. */
export default function PerformancePage() {
  const performance = useQuery({
    queryKey: ['performance'],
    queryFn: () => api.get<PerformanceStat[]>('/api/dashboard/performance'),
  })

  const stats = performance.data ?? []

  const chartData = stats.map((stat) => ({
    name: stat.assessmentName,
    You: percentage(stat.marksObtained, stat.maxMarks),
    Average: percentage(stat.classAverage, stat.maxMarks),
    Highest: percentage(stat.classHighest, stat.maxMarks),
  }))

  return (
    <>
      <PageHeader title="My results" description="How you are doing, compared with your cohort." />

      {performance.isLoading ? (
        <Card>
          <Loading rows={4} />
        </Card>
      ) : stats.length === 0 ? (
        <Card>
          <EmptyState title="No results yet" hint="Marks appear here once a teacher publishes them." />
        </Card>
      ) : (
        <>
          <Card title="Comparison">
            <div style={{ width: '100%', height: 300 }}>
              <ResponsiveContainer>
                <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 8, left: -16 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: 'var(--text-muted)' }} />
                  <YAxis unit="%" domain={[0, 100]} tick={{ fontSize: 12, fill: 'var(--text-muted)' }} />
                  <Tooltip
                    formatter={(value: number) => `${value}%`}
                    contentStyle={{
                      background: 'var(--surface)',
                      border: '1px solid var(--border)',
                      borderRadius: 8,
                      color: 'var(--text)',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="You" fill="var(--accent)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="Average" fill="var(--text-muted)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="Highest" fill="var(--success)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card title="Every result">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Assessment</th>
                    <th>Course</th>
                    <th>Your marks</th>
                    <th>Class average</th>
                    <th>Highest</th>
                    <th>Percentile</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.map((stat) => (
                    <tr key={stat.id}>
                      <td>{stat.assessmentName}</td>
                      <td className="small muted">{stat.courseName}</td>
                      <td className="mono">
                        {stat.marksObtained} / {stat.maxMarks}
                      </td>
                      <td className="mono">{stat.classAverage}</td>
                      <td className="mono">{stat.classHighest}</td>
                      <td className="mono">{stat.percentile}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </>
  )
}

function percentage(value: number, outOf: number): number {
  if (outOf <= 0) return 0
  return Math.round((value / outOf) * 100)
}
