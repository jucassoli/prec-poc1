package br.com.precatorios.exception

class ProcessoNaoEncontradoException(numero: String) :
    RuntimeException("Processo nao encontrado: $numero")
