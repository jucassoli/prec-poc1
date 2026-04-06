package br.com.precatorios.exception

class ProspeccaoNaoEncontradaException(id: Long) : RuntimeException("Prospeccao $id nao encontrada")
