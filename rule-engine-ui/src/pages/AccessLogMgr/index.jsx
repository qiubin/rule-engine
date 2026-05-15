import React, { useState, useEffect } from 'react'
import { Table, Button, DatePicker, Input, message, Popconfirm } from 'antd'
import { DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/access-logs'

function formatDateTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function AccessLogMgr() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 })
  const [total, setTotal] = useState(0)
  const [filters, setFilters] = useState({
    pageName: '',
    clientIp: '',
    requestPath: '',
    startTime: null,
    endTime: null,
  })

  const fetchLogs = async (page = 1, pageSize = 20) => {
    setLoading(true)
    try {
      const params = {
        page: page - 1,
        size: pageSize,
      }
      if (filters.pageName) params.pageName = filters.pageName
      if (filters.clientIp) params.clientIp = filters.clientIp
      if (filters.requestPath) params.requestPath = filters.requestPath
      if (filters.startTime) {
        const s = filters.startTime
        params.startTime = `${s.year()}-${String(s.month() + 1).padStart(2, '0')}-${String(s.date()).padStart(2, '0')} ${String(s.hour()).padStart(2, '0')}:${String(s.minute()).padStart(2, '0')}:${String(s.second()).padStart(2, '0')}`
      }
      if (filters.endTime) {
        const s = filters.endTime
        params.endTime = `${s.year()}-${String(s.month() + 1).padStart(2, '0')}-${String(s.date()).padStart(2, '0')} ${String(s.hour()).padStart(2, '0')}:${String(s.minute()).padStart(2, '0')}:${String(s.second()).padStart(2, '0')}`
      }

      const res = await axios.get(API, { params })
      setLogs(res.data.content || [])
      setTotal(res.data.totalElements || 0)
    } catch (e) {
      message.error('加载访问日志失败')
      setLogs([])
      setTotal(0)
    }
    setLoading(false)
  }

  useEffect(() => {
    fetchLogs(pagination.current, pagination.pageSize)
  }, [])

  const handleTableChange = (p) => {
    setPagination({ current: p.current, pageSize: p.pageSize })
    fetchLogs(p.current, p.pageSize)
  }

  const handleSearch = () => {
    setPagination({ current: 1, pageSize: pagination.pageSize })
    fetchLogs(1, pagination.pageSize)
  }

  const handleReset = () => {
    setFilters({
      pageName: '',
      clientIp: '',
      requestPath: '',
      startTime: null,
      endTime: null,
    })
    setPagination({ current: 1, pageSize: 20 })
    fetchLogs(1, 20)
  }

  const handleDelete = async (id) => {
    try {
      await axios.delete(`${API}/${id}`)
      message.success('删除成功')
      fetchLogs(pagination.current, pagination.pageSize)
    } catch (e) {
      message.error('删除失败')
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '页面名称', dataIndex: 'pageName', width: 120, render: (v) => v || '-' },
    { title: '请求路径', dataIndex: 'requestPath', ellipsis: true },
    { title: '请求方法', dataIndex: 'requestMethod', width: 80 },
    { title: '客户端IP', dataIndex: 'clientIp', width: 120 },
    {
      title: 'User-Agent',
      dataIndex: 'userAgent',
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '访问时间',
      dataIndex: 'accessTime',
      width: 170,
      render: formatDateTime,
    },
    {
      title: '操作',
      width: 80,
      render: (_, record) => (
        <Popconfirm title="确认删除?" onConfirm={() => handleDelete(record.id)}>
          <Button type="link" danger icon={<DeleteOutlined />} size="small">删除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <Input
          placeholder="页面名称"
          value={filters.pageName}
          onChange={(e) => setFilters({ ...filters, pageName: e.target.value })}
          style={{ width: 140 }}
          allowClear
        />
        <Input
          placeholder="客户端IP"
          value={filters.clientIp}
          onChange={(e) => setFilters({ ...filters, clientIp: e.target.value })}
          style={{ width: 140 }}
          allowClear
        />
        <Input
          placeholder="请求路径"
          value={filters.requestPath}
          onChange={(e) => setFilters({ ...filters, requestPath: e.target.value })}
          style={{ width: 200 }}
          allowClear
        />
        <DatePicker
          placeholder="开始时间"
          value={filters.startTime}
          onChange={(v) => setFilters({ ...filters, startTime: v })}
          showTime
          style={{ width: 180 }}
        />
        <DatePicker
          placeholder="结束时间"
          value={filters.endTime}
          onChange={(v) => setFilters({ ...filters, endTime: v })}
          showTime
          style={{ width: 180 }}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>查询</Button>
        <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={logs}
        loading={loading}
        bordered
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (t) => `共 ${t} 条`,
        }}
        onChange={handleTableChange}
      />
    </div>
  )
}
