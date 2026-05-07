import React, { useMemo } from 'react'
import ReactFlow, { Background } from 'reactflow'
import { nodeTypes } from '../Nodes'
import 'reactflow/dist/style.css'
import '../Nodes/node-styles.css'

export default function FlowCanvasViewer({ nodes, edges, highlightedNodeIds = [] }) {
  const displayNodes = useMemo(() => {
    if (!nodes) return []
    const highlightSet = new Set(highlightedNodeIds)
    return nodes.map(n => ({
      ...n,
      data: {
        ...n.data,
        highlighted: highlightSet.has(n.id)
      }
    }))
  }, [nodes, highlightedNodeIds])

  const displayEdges = useMemo(() => {
    if (!edges) return []
    return edges
  }, [edges])

  return (
    <div style={{ width: '100%', height: '100%', minHeight: 400 }}>
      <ReactFlow
        nodes={displayNodes}
        edges={displayEdges}
        nodeTypes={nodeTypes}
        fitView
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        panOnDrag={true}
        zoomOnScroll={true}
        zoomOnPinch={true}
        zoomOnDoubleClick={true}
      >
        <Background variant="dots" gap={12} size={1} />
      </ReactFlow>
    </div>
  )
}
